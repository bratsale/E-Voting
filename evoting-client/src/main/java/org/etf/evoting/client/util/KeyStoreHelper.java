package org.etf.evoting.client.util;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

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

    /**
     * Konvertuje Java PrivateKey objekat u PEM formatirani String (korisno za slanje preko TallyRequest).
     */
    public static String convertPrivateKeyToPem(PrivateKey privateKey) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(privateKey);
            pemWriter.flush();
        }
        return sw.toString();
    }

    /**
     * Otvara JavaFX FileChooser za izbor privatnog ključa (.pem ili .key) i vraća njegov sadržaj u Stringu.
     */
    public static String selectAndReadPemKey(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Izaberite privatni ključ organizatora (.pem)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PEM Ključevi (*.pem, *.key)", "*.pem", "*.key"),
                new FileChooser.ExtensionFilter("Svi fajlovi (*.*)", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(ownerWindow);
        if (selectedFile != null) {
            try {
                return Files.readString(selectedFile.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}