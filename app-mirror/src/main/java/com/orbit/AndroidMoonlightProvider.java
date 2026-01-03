package com.orbit;

import android.app.Activity;

import androidx.annotation.NonNull;

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
import java.security.cert.X509Certificate;
import java.util.List;
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
        b();
        NvHTTP httpConn = createNvHTTP(sunshineAddr, localIp);
        NvApp app = httpConn.getAppByName("Desktop");
//        a(httpConn, PlatformBinding.getCryptoProvider(context).getPemEncodedClientCertificate());
        ComputerDetails computer = httpConn.getComputerDetails(true);
        ComputerDetails persistent = new ComputerDatabaseManager(context).getComputerByUUID(computer.uuid);
        computer.serverCert = persistent.serverCert;
        /*
        computer.activeAddress = new ComputerDetails.AddressTuple("192.168.3.19", 47989);
        computer.manualAddress = new ComputerDetails.AddressTuple("192.168.3.19", 47989);
        computer.ipv6Address = new ComputerDetails.AddressTuple("[2408:8207:78b2:9830:7407:daff:ebb9:a8d3]", 47989);
        computer.macAddress = "c0:18:85:20:48:ac";
        computer.pairState = PairingManager.PairState.PAIRED;
         */
        computer.activeAddress = new ComputerDetails.AddressTuple("192.168.3.19", 47989);
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

    @NonNull
    private NvHTTP createNvHTTP(String sunshineAddr, String localIp) throws IOException {
        if(true) {
            return new NvHTTP(new ComputerDetails.AddressTuple("192.168.3.19", 47989), 47984, "ignored", null, PlatformBinding.getCryptoProvider(context));
        }
        int port;
        String sunshineIp;
        Matcher matcher = Pattern.compile(":\\d+$", 0).matcher(sunshineAddr);
        if(matcher.find()) {
            port = Integer.parseInt(matcher.group(0));
            sunshineIp = sunshineAddr.substring(0, matcher.start());
        } else {
            port = 47989;
            sunshineIp = sunshineAddr;
        }
        ComputerDetails.AddressTuple addressTuple = new ComputerDetails.AddressTuple(sunshineIp, port);
        SocketFactory socketFactory = new BoundSocketFactory(InetAddress.getByName(localIp));
        return NvHTTPFactory.create(addressTuple, port - 5, "ignored", null, PlatformBinding.getCryptoProvider(context), socketFactory);
    }

    private void doStart(NvApp app, ComputerDetails computer) {
        ComputerManagerService.ComputerManagerBinder mockBinder = new ComputerManagerService().new ComputerManagerBinder() {
            public String getUniqueId() {
                if (managerBinder == null) {
                    managerBinder = new IdentityManager(context);
                }
                return managerBinder.getUniqueId();
            }
        };
        ServerHelper.doStart(context, app, computer, mockBinder);
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

    private void b() {
        List<ComputerDetails> allComputers = new ComputerDatabaseManager(this.context).getAllComputers();
    }

    private void a(NvHTTP http, byte[] pemCertBytes) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        PairingManager pm = http.getPairingManager();

        Method generateRandomBytes = PairingManager.class.getDeclaredMethod("generateRandomBytes", int.class);
        if(!generateRandomBytes.isAccessible()) {
            generateRandomBytes.setAccessible(true);
        }
        // Generate a salt for hashing the PIN
        byte[] salt = (byte[]) generateRandomBytes.invoke(pm,16);

        Method bytesToHex = NvHTTP.class.getDeclaredMethod("bytesToHex", byte[].class);
        if(!bytesToHex.isAccessible()) {
            bytesToHex.setAccessible(true);
        }
        Method executePairingCommand = NvHTTP.class.getDeclaredMethod("executePairingCommand", String.class, boolean.class);
        if(!executePairingCommand.isAccessible()) {
            executePairingCommand.setAccessible(true);
        }
        // Send the salt and get the server cert. This doesn't have a read timeout
        // because the user must enter the PIN before the server responds
        String getCert = (String) executePairingCommand.invoke(http, "phrase=getservercert&salt="+
                        bytesToHex.invoke(null, salt)+"&clientcert="+bytesToHex.invoke(null, pemCertBytes),
                false);
        Method getXmlString = NvHTTP.class.getDeclaredMethod("getXmlString", String.class, String.class, boolean.class);
        if(!getXmlString.isAccessible()) {
            getXmlString.setAccessible(true);
        }
        if (!getXmlString.invoke(http, getCert, "paired", true).equals("1")) {
            throw new IllegalStateException(PairingManager.PairState.FAILED.name());
        }

        Method extractPlainCert = PairingManager.class.getDeclaredMethod("extractPlainCert", String.class);
        if(!extractPlainCert.isAccessible()) {
            extractPlainCert.setAccessible(true);
        }
        // Save this cert for retrieval later
        X509Certificate serverCert = (X509Certificate) extractPlainCert.invoke(pm, getCert);
        if (serverCert == null) {
            // Attempting to pair while another device is pairing will cause GFE
            // to give an empty cert in the response.
            http.unpair();
            throw new IllegalStateException(PairingManager.PairState.ALREADY_IN_PROGRESS.name());
        }

        Method setServerCert = NvHTTP.class.getDeclaredMethod("setServerCert", X509Certificate.class);
        if(!setServerCert.isAccessible()) {
            setServerCert.setAccessible(true);
        }
        // Require this cert for TLS to this host
        setServerCert.invoke(http, serverCert);
    }
}