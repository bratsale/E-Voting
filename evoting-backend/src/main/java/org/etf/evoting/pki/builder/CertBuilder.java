package org.etf.evoting.pki.builder;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CertBuilder {

  // 1. Izgradnja samopotpisanog Root CA sertifikata
  public static X509Certificate buildRootCertificate(KeyPair keyPair, String subjectDN) throws Exception {
    X500Name name = new X500Name(subjectDN);
    BigInteger serialNumber = new BigInteger(64, new SecureRandom());
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (3650L * 24 * 60 * 60 * 1000)); // 10 godina

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        name, serialNumber, notBefore, notAfter, name, keyPair.getPublic());

    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

    return signCertificate(certBuilder, keyPair.getPrivate());
  }

  // 2. Izgradnja podređenog (Subordinate) CA sertifikata
  public static X509Certificate buildSubordinateCertificate(
      KeyPair subKeyPair, String subDN, X509Certificate issuerCert, PrivateKey issuerPrivateKey) throws Exception {

    X500Name issuerName = X500Name.getInstance(issuerCert.getSubjectX500Principal().getEncoded());
    X500Name subjectName = new X500Name(subDN);
    BigInteger serialNumber = new BigInteger(64, new SecureRandom());
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (365L * 2 * 24 * 60 * 60 * 1000)); // 2 godine

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        issuerName, serialNumber, notBefore, notAfter, subjectName, subKeyPair.getPublic());

    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));

    return signCertificate(certBuilder, issuerPrivateKey);
  }

  // 3. Izgradnja sertifikata za krajnjeg korisnika
  public static X509Certificate buildUserCertificate(
      KeyPair userKeyPair, String userDN, X509Certificate caCert, PrivateKey caPrivateKey) throws Exception {

    X500Name issuerName = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
    X500Name subjectName = new X500Name(userDN);
    BigInteger serialNumber = new BigInteger(64, new SecureRandom());
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (365L * 24 * 60 * 60 * 1000)); // 1 godina

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        issuerName, serialNumber, notBefore, notAfter, subjectName, userKeyPair.getPublic());

    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certBuilder.addExtension(Extension.keyUsage, true,
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

    return signCertificate(certBuilder, caPrivateKey);
  }

  // Pomoćna interna metoda za potpisivanje i konverziju
  private static X509Certificate signCertificate(X509v3CertificateBuilder builder, PrivateKey privateKey)
      throws Exception {
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(privateKey);
    X509CertificateHolder holder = builder.build(signer);
    return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
  }
}
