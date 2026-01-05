package com.orbit;

import android.app.Activity;

import aar.MoonlightProvider;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerService;
import com.limelight.computers.IdentityManager;
import com.limelight.nvstream.http.*;
import com.limelight.utils.ServerHelper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.SocketFactory;

public class AndroidMoonlightProvider implements MoonlightProvider {

    private final Activity context;
    private IdentityManager managerBinder;

    public AndroidMoonlightProvider(Activity context) {
        this.context = context;
    }

    @Override
    public void connectRemote(String sunshineAddr, String localIp) throws Exception {
        ComputerDetails.AddressTuple addressTuple = createAddressTuple(sunshineAddr);
        NvHTTP httpConn = createNvHTTP(addressTuple, localIp);
        NvApp app = httpConn.getAppByName("Desktop");
        ComputerDetails computer = httpConn.getComputerDetails(true);
        ComputerDetails persistent = new ComputerDatabaseManager(context).getComputerByUUID(computer.uuid);
        computer.serverCert = persistent.serverCert;
        computer.activeAddress = addressTuple;
        doStart(app, computer, localIp);
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
        NvHTTP httpConn = createNvHTTP(sunshineAddr, localIp);
        return paired(httpConn);
    }

    @Override
    public void pin(String sunshineAddr, String pin, String localIp) throws Exception {
        NvHTTP httpConn = createNvHTTP(sunshineAddr, localIp);
        PairingManager pm = httpConn.getPairingManager();
        pm.pair(httpConn.getServerInfo(true), pin);
        ComputerDetails computerDetails = httpConn.getComputerDetails(true);
        computerDetails.serverCert = pm.getPairedCert();
        new ComputerDatabaseManager(this.context).updateComputer(computerDetails);
    }

    @Override
    public void terminate() throws Exception {

    }

    @Override
    public void terminatePin() throws Exception {

    }

    private NvHTTP createNvHTTP(String sunshineAddr, String localIp) throws IOException {
        return createNvHTTP(createAddressTuple(sunshineAddr), localIp);
    }

    private NvHTTP createNvHTTP(ComputerDetails.AddressTuple addressTuple, String localIp) throws IOException {
        SocketFactory socketFactory = new BoundSocketFactory(InetAddress.getByName(localIp));
        return new NvHTTP(addressTuple, addressTuple.port - 5, "ignored", null, PlatformBinding.getCryptoProvider(context), socketFactory);
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

    private void doStart(NvApp app, ComputerDetails computer, String srcIP) {
        ComputerManagerService.ComputerManagerBinder mockBinder = new ComputerManagerService().new ComputerManagerBinder() {
            public String getUniqueId() {
                if (managerBinder == null) {
                    managerBinder = new IdentityManager(context);
                }
                return managerBinder.getUniqueId();
            }
        };
        ServerHelper.doStart(context, app, computer, srcIP, mockBinder);
    }

    private boolean paired(NvHTTP httpConn) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, IOException, InvocationTargetException {
        Field httpClientShortConnectTimeout = NvHTTP.class.getDeclaredField("httpClientShortConnectTimeout");
        if(!httpClientShortConnectTimeout.isAccessible()) {
            httpClientShortConnectTimeout.setAccessible(true);
        }
        OkHttpClient client = (OkHttpClient) httpClientShortConnectTimeout.get(httpConn);
        Method openHttpConnectionToString = NvHTTP.class.getDeclaredMethod("openHttpConnectionToString", OkHttpClient.class, HttpUrl.class, String.class);
        if(!openHttpConnectionToString.isAccessible()) {
            openHttpConnectionToString.setAccessible(true);
        }
        String serverInfo = (String) openHttpConnectionToString.invoke(httpConn, client, httpConn.getHttpsUrl(true), "serverinfo");
        Matcher matcher = Pattern.compile("<PairStatus>(\\d+)</PairStatus>").matcher(serverInfo);
        if(matcher.find()) {
            String pairedState = matcher.group(1);
            return pairedState.equals("1");
        }
        return false;
    }
}