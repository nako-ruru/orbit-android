package com.orbit;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import aar.Aar;
import aar.ViewerProvider;

import com.limelight.Game;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerService;
import com.limelight.computers.IdentityManager;
import com.limelight.nvstream.http.*;
import com.limelight.utils.ServerHelper;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidMoonlightProvider implements ViewerProvider {

    private final MainActivity context;
    private IdentityManager managerBinder;
    ActivityResultLauncher<Intent> mStartSettingsLauncher;

    public AndroidMoonlightProvider(MainActivity context) {
        this.context = context;

        mStartSettingsLauncher = context.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Aar.onConnectionTerminated(0);
                }
        );
    }

    @Override
    public void connectRemote(String sunshineAddr, String localIp) throws Exception {
        // 不再支持多本地 IP 绑定：统一使用系统默认路由
        ComputerDetails.AddressTuple addressTuple = createAddressTuple(sunshineAddr);
        NvHTTP httpConn = createNvHTTP(addressTuple);
        NvApp app = httpConn.getAppByName("Desktop");
        ComputerDetails computer = httpConn.getComputerDetails(true);
        if (computer.serverCert == null) {
            computer.serverCert = findServerCert(addressTuple.address);
        }
        computer.activeAddress = addressTuple;
        doStart(app, computer);
    }

    @Override
    public long getConnectionTerminatedTime() {
        return 0;
    }

    @Override
    public long getFirstVideoPackedTime() {
        return 0;
    }

    @Override
    public long getId() {
        return 500;
    }

    @Override
    public boolean paired(String sunshineAddr, String localIp) throws Exception {
        // 使用月光自带的 getPairState()，内部会根据是否已有固定证书选择 HTTPS/HTTP
        NvHTTP httpConn = createNvHTTP(sunshineAddr);
        return httpConn.getPairState() == PairingManager.PairState.PAIRED;
    }

    @Override
    public void pin(String sunshineAddr, String pin, String localIp) throws Exception {
        new Thread(() -> {
            try {
                ComputerDetails.AddressTuple addressTuple = createAddressTuple(sunshineAddr);
                NvHTTP httpConn = createNvHTTP(addressTuple);
                PairingManager pm = httpConn.getPairingManager();
                pm.pair(httpConn.getServerInfo(true), pin);
                ComputerDetails computerDetails = httpConn.getComputerDetails(true);
                computerDetails.serverCert = pm.getPairedCert();
                // 数据库不保存 activeAddress，把本次连接地址写进 manualAddress，
                // 下次 paired() 才能按地址找回这台主机的证书。
                computerDetails.manualAddress = addressTuple;
                new ComputerDatabaseManager(this.context).updateComputer(computerDetails);
            } catch (Exception ex) {
                Log.e("TAG", "pin: ", ex);
            }
        }).start();
    }

    @Override
    public void terminate() throws Exception {
    }

    @Override
    public void terminatePin() throws Exception {
    }

    private NvHTTP createNvHTTP(String sunshineAddr) throws IOException {
        return createNvHTTP(createAddressTuple(sunshineAddr));
    }

    private NvHTTP createNvHTTP(ComputerDetails.AddressTuple addressTuple) throws IOException {
        // 不绑定本地 IP，使用默认路由。
        // 带上此前配对保存的服务器证书，HTTPS 才会按固定证书校验。
        return new NvHTTP(addressTuple, addressTuple.port - 5, "ignored",
                findServerCert(addressTuple.address), PlatformBinding.getCryptoProvider(context));
    }

    /**
     * 查找这台主机已配对保存的服务器证书。
     * 数据库里不保存 activeAddress，所以这里把所有地址字段都比对一遍。
     */
    private X509Certificate findServerCert(String ip) {
        try {
            List<ComputerDetails> computers = new ComputerDatabaseManager(context).getAllComputers();
            X509Certificate onlyCert = null;
            int certCount = 0;

            for (ComputerDetails computer : computers) {
                if (computer.serverCert == null) {
                    continue;
                }
                certCount++;
                onlyCert = computer.serverCert;
                ComputerDetails.AddressTuple[] tuples = new ComputerDetails.AddressTuple[]{
                        computer.activeAddress,
                        computer.localAddress,
                        computer.remoteAddress,
                        computer.manualAddress,
                        computer.ipv6Address,
                };
                for (ComputerDetails.AddressTuple tuple : tuples) {
                    if (tuple != null && ip.equals(tuple.address)) {
                        return computer.serverCert;
                    }
                }
            }

            // 单机场景回退：证书只可能属于这一台主机（校验失败会自动走重新配对）
            if (certCount == 1) {
                return onlyCert;
            }
        } catch (Exception e) {
            Log.e("TAG", "findServerCert: ", e);
        }
        return null;
    }

    private ComputerDetails.AddressTuple createAddressTuple(String addr) {
        int port;
        String sunshineIp;
        Matcher matcher = Pattern.compile(":\\d+$", 0).matcher(addr);
        if(matcher.find()) {
            port = Integer.parseInt(matcher.group(0));
            sunshineIp = addr.substring(0, matcher.start());
        } else {
            port = 47989;
            sunshineIp = addr;
        }
        ComputerDetails.AddressTuple addressTuple = new ComputerDetails.AddressTuple(sunshineIp, port);
        return addressTuple;
    }

    private void doStart(NvApp app, ComputerDetails computer) {
        Game.listener = new NvConnectionListenerAdapter() {
            @Override
            public void connectionStarted() {
                Aar.onConnectionStarted();
            }
        };
        ComputerManagerService.ComputerManagerBinder mockBinder = new ComputerManagerService().new ComputerManagerBinder() {
            public String getUniqueId() {
                if (managerBinder == null) {
                    managerBinder = new IdentityManager(context);
                }
                return managerBinder.getUniqueId();
            }
        };
        doStart(app, computer, mockBinder);
    }

    private void doStart(NvApp app, ComputerDetails computer,
                               ComputerManagerService.ComputerManagerBinder managerBinder) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(context, context.getResources().getString(com.limelight.R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        mStartSettingsLauncher.launch(ServerHelper.createStartIntent(context, app, computer, managerBinder));
    }
}
