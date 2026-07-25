package org.etf.evoting.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

public class KeyStoreHelper {

    /**
     * Izvlači privatni ključ iz .p12 fajla na osnovu korisničkog imena (alias) i lozinke.
     */
    public static PrivateKey loadPrivateKeyFromP12(File p12File, String alias, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(p12File)) {
            keyStore.load(fis, password.toCharArray());
        }
        return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
    }

    /**
     * Potpisuje proizvoljne podatke privatnim ključem glasača (SHA256withRSA).
     */
    public static String signData(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
}