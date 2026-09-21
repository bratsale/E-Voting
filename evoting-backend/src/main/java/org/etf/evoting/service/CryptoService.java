package org.etf.evoting.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.stereotype.Service;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

@Service
public class CryptoService {

  private static final String HMAC_SECRET = "MojSuperTajniHmacKljucZaMetapodatke123!";


  static {
    if (Security.getProvider("BC") == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  public SecretKey generateAESKey() throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    return keyGen.generateKey();
  }

  public byte[] encryptVoteWithAES(String voteContent, SecretKey aesKey, byte[] iv) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
    return cipher.doFinal(voteContent.getBytes(StandardCharsets.UTF_8));
  }

  public byte[] encryptAESKeyWithOrganizerPublicKey(SecretKey aesKey, PublicKey organizerPublicKey) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
    cipher.init(Cipher.ENCRYPT_MODE, organizerPublicKey);
    return cipher.doFinal(aesKey.getEncoded());
  }

  public String calculateMetadataHMAC(String metadataData) throws Exception {
    Mac sha256HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKey = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    sha256HMAC.init(secretKey);
    byte[] hmacBytes = sha256HMAC.doFinal(metadataData.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hmacBytes);
  }

  public SecretKey decryptAESKeyWithOrganizerPrivateKey(byte[] encryptedAesKey, PrivateKey organizerPrivateKey) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
    cipher.init(Cipher.DECRYPT_MODE, organizerPrivateKey);
    byte[] decryptedKeyBytes = cipher.doFinal(encryptedAesKey);
    return new SecretKeySpec(decryptedKeyBytes, "AES");
  }

  public String decryptVoteWithAES(byte[] encryptedVote, SecretKey aesKey, byte[] iv) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
    byte[] decryptedBytes = cipher.doFinal(encryptedVote);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }

  public String generateAndSaveUserCertificate(String username, String roleStr, String p12Password) throws Exception {
    boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(roleStr);

    Path pkiPath = Paths.get("pki");
    if (!Files.exists(pkiPath)) {
      pkiPath = Paths.get("evoting-backend", "pki");
    }

    String caDir = isOrganizer ? "organizacioni-ca" : "glasacki-ca";
    String caFileName = isOrganizer ? "organizacioni" : "glasacki";

    Path caCertPath = pkiPath.resolve(caDir).resolve(caFileName + ".crt");
    Path caKeyPath = pkiPath.resolve(caDir).resolve(caFileName + ".key");

    Path rootCertPath = pkiPath.resolve("root-ca").resolve("root.crt");

    if (!Files.exists(caCertPath) || !Files.exists(caKeyPath)) {
      throw new FileNotFoundException("Nisu pronađeni Sub-CA fajlovi na putanji: " + caCertPath.toAbsolutePath());
    }
    if (!Files.exists(rootCertPath)) {
      throw new FileNotFoundException("Nije pronađen Root CA sertifikat na putanji: " + rootCertPath.toAbsolutePath());
    }

    X509Certificate caCert = loadCertificateFromPemFile(caCertPath.toFile());
    PrivateKey caPrivateKey = loadPrivateKeyFromPemFile(caKeyPath.toFile());
    X509Certificate rootCert = loadCertificateFromPemFile(rootCertPath.toFile());

    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
    keyPairGen.initialize(2048);
    KeyPair userKeyPair = keyPairGen.generateKeyPair();

    X509Certificate userCert = createSignedCertificate(username, userKeyPair.getPublic(), caCert, caPrivateKey);

    String targetSubfolder = isOrganizer ? "organizatori" : "glasaci";
    Path userCertsDir = pkiPath.resolve("korisnici").resolve(targetSubfolder);
    Files.createDirectories(userCertsDir);

    Path p12FilePath = userCertsDir.resolve(username + ".p12");
    saveToPkcs12(p12FilePath.toFile(), username, userKeyPair.getPrivate(), userCert, caCert, rootCert, p12Password);

    System.out.println("Generisan .p12 na: " + p12FilePath.toAbsolutePath());

    return convertToPem(userCert);
  }

  private X509Certificate createSignedCertificate(String username, PublicKey userPublicKey, X509Certificate caCert, PrivateKey caPrivateKey) throws Exception {
    long now = System.currentTimeMillis();
    Date startDate = new Date(now - 60000L); // 1 min u prošlost
    Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);

    X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
    X500Name subject = new X500Name("CN=" + username + ", O=ETF Banja Luka, C=BA");
    BigInteger serialNumber = BigInteger.valueOf(now);

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            startDate,
            endDate,
            subject,
            userPublicKey
    );

    JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

    certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true,
            new org.bouncycastle.asn1.x509.BasicConstraints(false));

    certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true,
            new org.bouncycastle.asn1.x509.KeyUsage(
                    org.bouncycastle.asn1.x509.KeyUsage.digitalSignature |
                            org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment
            ));

    certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false,
            extUtils.createSubjectKeyIdentifier(userPublicKey));
    certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.authorityKeyIdentifier, false,
            extUtils.createAuthorityKeyIdentifier(caCert));

    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(caPrivateKey);

    X509Certificate userCert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
    userCert.verify(caCert.getPublicKey());

    return userCert;
  }

  private void saveToPkcs12(
          File outFile,
          String alias,
          PrivateKey privateKey,
          X509Certificate userCert,
          X509Certificate caCert,
          X509Certificate rootCert,
          String password) throws Exception {

    KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
    keyStore.load(null, null);

    X509Certificate[] chain = new X509Certificate[]{ userCert, caCert, rootCert };

    keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), chain);

    try (FileOutputStream fos = new FileOutputStream(outFile)) {
      keyStore.store(fos, password.toCharArray());
    }
  }

  private X509Certificate loadCertificateFromPemFile(File file) throws Exception {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    try (FileInputStream fis = new FileInputStream(file)) {
      return (X509Certificate) factory.generateCertificate(fis);
    }
  }

  private PrivateKey loadPrivateKeyFromPemFile(File file) throws Exception {
    try (FileReader reader = new FileReader(file);
         PEMParser pemParser = new PEMParser(reader)) {
      Object object = pemParser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
        return converter.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) object).getPrivate();
      } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
        return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
      }
      throw new IllegalArgumentException("Nepoznat format privatnog ključa u fajlu: " + file.getName());
    }
  }

  public String convertToPem(X509Certificate cert) throws Exception {
    StringWriter writer = new StringWriter();
    try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(cert);
    }
    return writer.toString();
  }

  public X509Certificate convertPemToCertificate(String certPem) throws Exception {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    ByteArrayInputStream is = new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8));
    return (X509Certificate) factory.generateCertificate(is);
  }

  public boolean verifySignature(String certificatePem, String data, String signatureBase64) {
    try {
      X509Certificate certificate = convertPemToCertificate(certificatePem);
      certificate.checkValidity();
      PublicKey publicKey = certificate.getPublicKey();

      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(data.getBytes(StandardCharsets.UTF_8));

      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      return signature.verify(signatureBytes);
    } catch (Exception e) {
      return false;
    }
  }

  public void validateUserCertificate(String certificatePem, String expectedUsername, String roleStr) throws Exception {
    if (certificatePem == null || certificatePem.isBlank()) {
      throw new IllegalArgumentException("Digitalni sertifikat je obavezan za prijavu.");
    }

    X509Certificate userCert = convertPemToCertificate(certificatePem);

    userCert.checkValidity();

    Path pkiPath = Paths.get("pki");
    if (!Files.exists(pkiPath)) {
      pkiPath = Paths.get("evoting-backend", "pki");
    }

    boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(roleStr);
    String caDir = isOrganizer ? "organizacioni-ca" : "glasacki-ca";
    String caFileName = isOrganizer ? "organizacioni" : "glasacki";
    Path caCertPath = pkiPath.resolve(caDir).resolve(caFileName + ".crt");

    X509Certificate caCert = loadCertificateFromPemFile(caCertPath.toFile());

    userCert.verify(caCert.getPublicKey());

    Path crlPath = pkiPath.resolve(caDir).resolve(caFileName + ".crl");
    if (Files.exists(crlPath)) {
      java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
      try (FileInputStream fis = new FileInputStream(crlPath.toFile())) {
        java.security.cert.X509CRL crl = (java.security.cert.X509CRL) cf.generateCRL(fis);
        if (crl.isRevoked(userCert)) {
          throw new SecurityException("Sertifikat za korisnika '" + expectedUsername + "' je OPOZVAN (nalazi se na CRL listi)!");
        }
      }
    }

    String dn = userCert.getSubjectX500Principal().getName();
    String certUsername = extractCNFromDN(dn);

    if (!expectedUsername.equalsIgnoreCase(certUsername)) {
      throw new SecurityException("Priloženi sertifikat pripada korisniku '" + certUsername + "', a ne '" + expectedUsername + "'.");
    }
  }

  private String extractCNFromDN(String dn) {
    for (String part : dn.split(",")) {
      part = part.trim();
      if (part.toUpperCase().startsWith("CN=")) {
        return part.substring(3);
      }
    }
    return "";
  }

  public PrivateKey convertPemToPrivateKey(String privateKeyPem) throws Exception {
    try (Reader reader = new StringReader(privateKeyPem);
         PEMParser pemParser = new PEMParser(reader)) {
      Object object = pemParser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

      if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
        return converter.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) object).getPrivate();
      } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
        return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
      }

      throw new IllegalArgumentException("Nepoznat format privatnog ključa u pruženo stringu.");
    }
  }

  public PublicKey convertPemToPublicKey(String publicKeyPem) throws Exception {
    String cleanPem = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");

    byte[] decoded = Base64.getDecoder().decode(cleanPem);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
    return keyFactory.generatePublic(keySpec);
  }
}