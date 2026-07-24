package org.etf.evoting.pki.model;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public abstract class CertificateAuthority {
  protected X509Certificate caCertificate;
  protected PrivateKey privateKey;
  protected String directoryPath;

  public CertificateAuthority(String directoryPath) {
    this.directoryPath = directoryPath;
  }

  public X509Certificate getCaCertificate() {
    return caCertificate;
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  public String getDirectoryPath() {
    return directoryPath;
  }
}
