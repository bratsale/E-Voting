package org.etf.evoting.pki.model;

import org.etf.evoting.pki.builder.CertBuilder;
import org.etf.evoting.pki.manager.KeyManager;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SubordinateCA extends CertificateAuthority {

  // Konstruktor za učitavanje postojećeg Subordinate CA sa diska
  public SubordinateCA(String directoryPath, String keyName, String certName) throws Exception {
    super(directoryPath);
    File keyFile = new File(directoryPath, keyName + ".key");
    File certFile = new File(directoryPath, certName + ".crt");

    if (!keyFile.exists() || !certFile.exists()) {
      throw new IllegalStateException("Subordinate CA fajlovi ne postoje na putanji. " +
          "Mora se kreirati preko RootCA objekta.");
    }

    this.privateKey = KeyManager.loadPrivateKeyFromPEM(keyFile.getAbsolutePath());
    try (FileInputStream fis = new FileInputStream(certFile)) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
      this.caCertificate = (X509Certificate) cf.generateCertificate(fis);
    }
  }

  // Glavna uloga Sub-CA: Izdavanje sertifikata krajnjem korisniku (Glasaču ili
  // Organizatoru)
  public X509Certificate issueUserCertificate(KeyPair userKeyPair, String userDN) throws Exception {
    return CertBuilder.buildUserCertificate(userKeyPair, userDN, this.caCertificate, this.privateKey);
  }
}
