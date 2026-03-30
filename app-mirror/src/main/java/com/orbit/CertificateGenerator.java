package com.orbit;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;


public class CertificateGenerator {

    public static void generateSelfSignedCertificate(String certPath, String keyPath)
            throws CertificateException, NoSuchAlgorithmException, IOException, OperatorCreationException, NoSuchProviderException {
        // 1. 生成 RSA 2048 密钥对 (对应 -newkey rsa:2048)
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 2. 设置有效期 (对应 -days 7305)
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 7305L * 24 * 60 * 60 * 1000);

        // 3. 设置证书主题 (对应 -subj "/CN=Sun shine Gamestream")
        X500Name dnName = new X500Name("CN=Sun shine Gamestream");
        BigInteger certSerialNumber = new BigInteger(64, new SecureRandom());

        // 4. 构建自签名证书
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        X509CertificateHolder certHolder = certBuilder.build(contentSigner);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 5. 保存私钥为 PEM (对应 -keyout)
        saveToPEM(keyPair.getPrivate(), keyPath);

        // 6. 保存证书为 PEM (对应 -out)
        saveToPEM(certificate, certPath);
    }

    private static void saveToPEM(Object object, String path) throws IOException {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(path))) {
            pemWriter.writeObject(object);
        }
    }
}