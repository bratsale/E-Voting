package org.etf.evoting.pki;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class PKIUtils {

  // 1. Pomoćna metoda za generisanje RSA para ključeva
  public static KeyPair generateRSAKeyPair(int keySize) throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(keySize, new SecureRandom());
    return keyPairGenerator.generateKeyPair();
  }

  // 2. Metoda za kreiranje samopotpisanog Root CA sertifikata
  public static X509Certificate createRootCACertificate(KeyPair keyPair, String subjectDN) throws Exception {
    X500Name name = new X500Name(subjectDN);

    // Serijski broj sertifikata (za Root CA uzimamo nasumičan veliki broj)
    BigInteger serialNumber = new BigInteger(64, new SecureRandom());

    // Vremenski period validnosti (npr. 10 godina za Root CA)
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (3650L * 24 * 60 * 60 * 1000)); // 10 godina

    // Pravimo builder za X509v3 sertifikat
    // Kod samopotpisanog, issuer (izdavač) i subject (vlasnik) su ISTI
    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        name, // Issuer
        serialNumber, // Serial Number
        notBefore, // Valid From
        notAfter, // Valid To
        name, // Subject
        keyPair.getPublic() // Javni ključ koji se pakuje u sertifikat
    );

    // EKSTENZIJA: Basic Constraints -> Označavamo da je ovo CA sertifikat i da može
    // potpisivati druge
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

    // Kreiramo potpisivač (Signer) koji koristi PRIVATNI ključ Root CA za
    // potpisivanje samog sebe
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider("BC")
        .build(keyPair.getPrivate());

    // Izgradnja sertifikata
    X509CertificateHolder certHolder = certBuilder.build(signer);

    // Konvertujemo Bouncy Castle objekat u standardni Java X509Certificate
    return new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certHolder); // Ovdje ide getCertificate umjesto build!
  }

  // 3. Metoda kojom Root CA potpisuje podređeni (Subordinate) CA sertifikat
  public static X509Certificate createSubordinateCACertificate(
      KeyPair subCAKeyPair,
      String subCASubjectDN,
      X509Certificate rootCert,
      PrivateKey rootPrivateKey) throws Exception {

    // Uzimamo ime izdavača (Root CA) iz njegovog sertifikata
    X500Name issuerName = X500Name.getInstance(rootCert.getSubjectX500Principal().getEncoded());
    X500Name subjectName = new X500Name(subCASubjectDN);

    BigInteger serialNumber = new BigInteger(64, new SecureRandom());
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (365L * 2 * 24 * 60 * 60 * 1000)); // Validnost 2 godine

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        issuerName, // Izdavač je Root CA
        serialNumber,
        notBefore,
        notAfter,
        subjectName, // Vlasnik je podređeni CA
        subCAKeyPair.getPublic());

    // EKSTENZIJA: Basic Constraints -> Pošto je i ovo CA tijelo, stavljamo true.
    // Prosljeđujemo 0 kao "pathLenConstraint" što znači da ovaj sub-CA može
    // potpisivati krajnje korisnike (glasče/organizatore),
    // ali ne može stvarati nova pod-CA tijela ispod sebe.
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));

    // Potpisujemo PRIVATNIM ključem Root CA
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider("BC")
        .build(rootPrivateKey);

    X509CertificateHolder certHolder = certBuilder.build(signer);
    return new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certHolder);
  }

  // 4. Metoda za čuvanje objekata (ključa ili sertifikata) u PEM formatu na disk
  public static void saveToPEM(Object object, String directoryPath, String fileName) throws Exception {
    java.nio.file.Path dirPath = java.nio.file.Paths.get(directoryPath);
    if (!java.nio.file.Files.exists(dirPath)) {
      java.nio.file.Files.createDirectories(dirPath);
    }

    java.nio.file.Path filePath = dirPath.resolve(fileName);
    try (java.io.FileWriter fw = new java.io.FileWriter(filePath.toFile());
        org.bouncycastle.openssl.jcajce.JcaPEMWriter pemWriter = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(fw)) {

      pemWriter.writeObject(object);
      pemWriter.flush();
    }
  }

  // 5. Metoda za izdavanje sertifikata krajnjem korisniku (Glasaču ili
  // Organizatoru) od strane odgovarajućeg CA
  public static X509Certificate createUserCertificate(
      KeyPair userKeyPair,
      String userSubjectDN,
      X509Certificate caCert,
      PrivateKey caPrivateKey) throws Exception {

    X500Name issuerName = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
    X500Name subjectName = new X500Name(userSubjectDN);

    BigInteger serialNumber = new BigInteger(64, new SecureRandom());
    Date notBefore = new Date();
    Date notAfter = new Date(notBefore.getTime() + (365L * 24 * 60 * 60 * 1000)); // Validnost 1 godina za korisnike

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        issuerName,
        serialNumber,
        notBefore,
        notAfter,
        subjectName,
        userKeyPair.getPublic());

    // EKSTENZIJA: Basic Constraints -> Pošto je ovo krajnji korisnik (a NE CA
    // tijelo), stavljamo false!
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

    // EKSTENZIJA: Key Usage -> Definišemo za šta se ključ smije koristiti
    // Krajnji korisnik koristi ključ za digitalni potpis (Digital Signature) i
    // nelatentnost (Non-Repudiation)
    certBuilder.addExtension(Extension.keyUsage, true,
        new org.bouncycastle.asn1.x509.KeyUsage(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature |
                org.bouncycastle.asn1.x509.KeyUsage.nonRepudiation));

    // Potpisujemo privatnim ključem CA tijela (Organizacionog ili Glasačkog)
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider("BC")
        .build(caPrivateKey);

    X509CertificateHolder certHolder = certBuilder.build(signer);
    return new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certHolder);
  }
}
