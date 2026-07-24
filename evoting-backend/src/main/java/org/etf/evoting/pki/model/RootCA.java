package org.etf.evoting.pki.model;

import org.etf.evoting.pki.builder.*;
import org.etf.evoting.pki.manager.*;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class RootCA extends CertificateAuthority {

  public RootCA(String directoryPath, String subjectDN) throws Exception {
    super(directoryPath);

    File keyFile = new File(directoryPath, "root.key");
    File certFile = new File(directoryPath, "root.crt");

    // Ako fajlovi već postoje na disku, učitaj ih!
    if (keyFile.exists() && certFile.exists()) {
      this.privateKey = KeyManager.loadPrivateKeyFromPEM(keyFile.getAbsolutePath());

      try (FileInputStream fis = new FileInputStream(certFile)) {
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        this.caCertificate = (X509Certificate) cf.generateCertificate(fis);
      }
    } else {
      // Ako ne postoje, generiši novi Root CA
      KeyPair kp = KeyManager.generateRSAKeyPair(4096);
      this.privateKey = kp.getPrivate();
      this.caCertificate = CertBuilder.buildRootCertificate(kp, subjectDN);

      // Snimi na disk za iduće pokretanje aplikacije
      KeyManager.saveToPEM(this.privateKey, directoryPath, "root.key");
      KeyManager.saveToPEM(this.caCertificate, directoryPath, "root.crt");
    }
  }

  // Moć Root CA: Kreiranje i potpisivanje podređenog CA tijela
  public SubordinateCA createSubordinateCA(String subDirPath, String subDN, String keyName, String certName)
      throws Exception {
    File keyFile = new File(subDirPath, keyName + ".key");
    File certFile = new File(subDirPath, certName + ".crt");

    // Ako podređeni CA već postoji, konstruktor SubordinateCA će ga sam učitati
    if (keyFile.exists() && certFile.exists()) {
      return new SubordinateCA(subDirPath, keyName, certName);
    }

    // Ako ne postoji, generiši ga i POTPIŠI sa ovim Root CA ključem i sertifikatom
    KeyPair subKeyPair = KeyManager.generateRSAKeyPair(2048);
    X509Certificate subCert = CertBuilder.buildSubordinateCertificate(
        subKeyPair, subDN, this.caCertificate, this.privateKey);

    KeyManager.saveToPEM(subKeyPair.getPrivate(), subDirPath, keyName + ".key");
    KeyManager.saveToPEM(subCert, subDirPath, certName + ".crt");

    return new SubordinateCA(subDirPath, keyName, certName);
  }
}
