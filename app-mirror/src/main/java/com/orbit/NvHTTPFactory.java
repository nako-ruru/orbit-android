package com.orbit;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvHTTP;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;

import okhttp3.OkHttpClient;

public class NvHTTPFactory {

    public static NvHTTP create(ComputerDetails.AddressTuple address, int httpsPort, String uniqueId, X509Certificate serverCert, LimelightCryptoProvider cryptoProvider, SocketFactory socketFactory) throws IOException {
        NvHTTP nvHTTP = new NvHTTP(address, httpsPort, uniqueId, serverCert, cryptoProvider);
        socketFactory(nvHTTP, "httpClientLongConnectTimeout", socketFactory);
        socketFactory(nvHTTP, "httpClientLongConnectNoReadTimeout", socketFactory);
        socketFactory(nvHTTP, "httpClientShortConnectTimeout", socketFactory);
        return nvHTTP;
    }

    private static void socketFactory(NvHTTP nvHTTP, String field, SocketFactory socketFactory) {
        try {
            Field f = NvHTTP.class.getDeclaredField(field);
            if(!f.isAccessible()) {
                f.setAccessible(true);
            }
            OkHttpClient prototype = (OkHttpClient) f.get(nvHTTP);
            OkHttpClient newOkHttpClient = prototype.newBuilder()
                    .socketFactory(socketFactory)
                    .build();
            f.set(nvHTTP, newOkHttpClient);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}