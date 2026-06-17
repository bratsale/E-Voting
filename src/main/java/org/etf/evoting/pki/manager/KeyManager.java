package org.etf.evoting.pki.manager;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;

public class KeyManager {

  // Generisanje RSA para ključeva
  public static KeyPair generateRSAKeyPair(int keySize) throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(keySize, new SecureRandom());
    return keyPairGenerator.generateKeyPair();
  }

  // Čuvanje bilo kojeg objekta (ključ, sertifikat) u PEM formatu
  public static void saveToPEM(Object object, String directoryPath, String fileName) throws Exception {
    Path dirPath = Paths.get(directoryPath);
    if (!Files.exists(dirPath)) {
      Files.createDirectories(dirPath);
    }

    Path filePath = dirPath.resolve(fileName);
    try (FileWriter fw = new FileWriter(filePath.toFile());
        JcaPEMWriter pemWriter = new JcaPEMWriter(fw)) {
      pemWriter.writeObject(object);
      pemWriter.flush();
    }
  }

  // Učitavanje privatnog ključa sa diska (PEM format)
  public static PrivateKey loadPrivateKeyFromPEM(String filePath) throws Exception {
    try (FileReader fr = new FileReader(filePath);
        PEMParser pemParser = new PEMParser(fr)) {

      Object object = pemParser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

      if (object instanceof PEMKeyPair) {
        return converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
      } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
        return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
      }

      throw new IllegalArgumentException("Fajl ne sadrzi validan privatni kljuc: " + filePath);
    }
  }
}
