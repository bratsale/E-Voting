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

  private static final String PKI_PATH = "pki"; // Putanja do tvog pki foldera
  private static final String HMAC_SECRET = "MojSuperTajniHmacKljucZaMetapodatke123!"; // U produkciji iz application.properties


  static {
    if (Security.getProvider("BC") == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }


  /**
   * 1. Generiše nasumični AES-256 simetrični ključ
   */
  public SecretKey generateAESKey() throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    return keyGen.generateKey();
  }

  /**
   * 2. Enkriptuje glas simetričnim AES ključem (AES/GCM/NoPadding)
   */
  public byte[] encryptVoteWithAES(String voteContent, SecretKey aesKey, byte[] iv) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
    return cipher.doFinal(voteContent.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 3. Enkriptuje AES simetrični ključ sa RSA javnim ključem Organizatora
   */
  public byte[] encryptAESKeyWithOrganizerPublicKey(SecretKey aesKey, PublicKey organizerPublicKey) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
    cipher.init(Cipher.ENCRYPT_MODE, organizerPublicKey);
    return cipher.doFinal(aesKey.getEncoded());
  }

  /**
   * 4. Izračunava HMAC-SHA256 za metapodatke glasanja radi očuvanja integriteta
   */
  public String calculateMetadataHMAC(String metadataData) throws Exception {
    Mac sha256HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKey = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    sha256HMAC.init(secretKey);
    byte[] hmacBytes = sha256HMAC.doFinal(metadataData.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hmacBytes);
  }

  /**
   * 5. Dešifruje AES ključ sa RSA privatnim ključem Organizatora (Prilikom brojanja glasova)
   */
  public SecretKey decryptAESKeyWithOrganizerPrivateKey(byte[] encryptedAesKey, PrivateKey organizerPrivateKey) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
    cipher.init(Cipher.DECRYPT_MODE, organizerPrivateKey);
    byte[] decryptedKeyBytes = cipher.doFinal(encryptedAesKey);
    return new SecretKeySpec(decryptedKeyBytes, "AES");
  }

  /**
   * 6. Dešifruje sam glas sa dešifrovanim AES ključem
   */
  public String decryptVoteWithAES(byte[] encryptedVote, SecretKey aesKey, byte[] iv) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
    byte[] decryptedBytes = cipher.doFinal(encryptedVote);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }

  /**
   * Glavna metoda za kreiranje i čuvanje korisničkog sertifikata i .p12 kontejnera.
   */
  public String generateAndSaveUserCertificate(String username, String roleStr, String p12Password) throws Exception {
    boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(roleStr); //[cite: 4]

    // 1. Odredi baznu putanju do PKI foldera[cite: 4]
    Path pkiPath = Paths.get("pki"); //[cite: 4]
    if (!Files.exists(pkiPath)) { //[cite: 4]
      pkiPath = Paths.get("evoting-backend", "pki"); //[cite: 4]
    }

    // 2. Putanje do Sub-CA (glasacki ili organizacioni)[cite: 4]
    String caDir = isOrganizer ? "organizacioni-ca" : "glasacki-ca"; //[cite: 4]
    String caFileName = isOrganizer ? "organizacioni" : "glasacki"; //[cite: 4]

    Path caCertPath = pkiPath.resolve(caDir).resolve(caFileName + ".crt"); //[cite: 4]
    Path caKeyPath = pkiPath.resolve(caDir).resolve(caFileName + ".key"); //[cite: 4]

    // TAČNA PUTANJA DO ROOT CA: root-ca/root.crt
    Path rootCertPath = pkiPath.resolve("root-ca").resolve("root.crt");

    if (!Files.exists(caCertPath) || !Files.exists(caKeyPath)) { //[cite: 4]
      throw new FileNotFoundException("Nisu pronađeni Sub-CA fajlovi na putanji: " + caCertPath.toAbsolutePath()); //[cite: 4]
    }
    if (!Files.exists(rootCertPath)) {
      throw new FileNotFoundException("Nije pronađen Root CA sertifikat na putanji: " + rootCertPath.toAbsolutePath());
    }

    // 3. Učitaj Sub-CA i Root CA sertifikate[cite: 4]
    X509Certificate caCert = loadCertificateFromPemFile(caCertPath.toFile()); //[cite: 4]
    PrivateKey caPrivateKey = loadPrivateKeyFromPemFile(caKeyPath.toFile()); //[cite: 4]
    X509Certificate rootCert = loadCertificateFromPemFile(rootCertPath.toFile());

    // 4. Generiši novi RSA par ključeva za korisnika[cite: 4]
    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA"); //[cite: 4]
    keyPairGen.initialize(2048); //[cite: 4]
    KeyPair userKeyPair = keyPairGen.generateKeyPair(); //[cite: 4]

    // 5. Napravi sertifikat potpisan od strane Sub-CA[cite: 4]
    X509Certificate userCert = createSignedCertificate(username, userKeyPair.getPublic(), caCert, caPrivateKey); //[cite: 4]

    // 6. Odredi podfolder za čuvanje korisnika[cite: 4]
    String targetSubfolder = isOrganizer ? "organizatori" : "glasaci"; //[cite: 4]
    Path userCertsDir = pkiPath.resolve("korisnici").resolve(targetSubfolder); //[cite: 4]
    Files.createDirectories(userCertsDir); //[cite: 4]

    // 7. Sačuvaj .p12 sa punim lancem[cite: 4]
    Path p12FilePath = userCertsDir.resolve(username + ".p12"); //[cite: 4]
    saveToPkcs12(p12FilePath.toFile(), username, userKeyPair.getPrivate(), userCert, caCert, rootCert, p12Password);

    System.out.println("✅ Generisan .p12 na: " + p12FilePath.toAbsolutePath()); //[cite: 4]

    return convertToPem(userCert); //[cite: 4]
  }

  private X509Certificate createSignedCertificate(String username, PublicKey userPublicKey, X509Certificate caCert, PrivateKey caPrivateKey) throws Exception {
    long now = System.currentTimeMillis();
    Date startDate = new Date(now - 60000L); // 1 minut u prošlost radi vremenskih odstupanja
    Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);

    // VAŽNO: Koristimo tačan Principal iz CA sertifikata za Issuer-a
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

    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(caPrivateKey);

    X509Certificate userCert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));

    // Provjera valjanosti u odnosu na CA ključ
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

    // VAŽNO: Navodimo "BC" (BouncyCastle) kao provider da KeyStore prihati lanac bez Sun JCE restrikcija
    KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
    keyStore.load(null, null); //[cite: 4]

    // Puni lanac od korisnika preko podređenog CA do Root CA
    X509Certificate[] chain = new X509Certificate[]{ userCert, caCert, rootCert };

    keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), chain); //[cite: 4]

    try (FileOutputStream fos = new FileOutputStream(outFile)) { //[cite: 4]
      keyStore.store(fos, password.toCharArray()); //[cite: 4]
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

    // 1. Konvertuj PEM u X509Certificate
    X509Certificate userCert = convertPemToCertificate(certificatePem);

    // 2. Provjeri vremensku važenost (da nije istekao)
    userCert.checkValidity();

    // 3. Učitaj odgovarajući CA sertifikat
    Path pkiPath = Paths.get("pki");
    if (!Files.exists(pkiPath)) {
      pkiPath = Paths.get("evoting-backend", "pki");
    }

    boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(roleStr);
    String caDir = isOrganizer ? "organizacioni-ca" : "glasacki-ca";
    String caFileName = isOrganizer ? "organizacioni" : "glasacki";
    Path caCertPath = pkiPath.resolve(caDir).resolve(caFileName + ".crt");

    X509Certificate caCert = loadCertificateFromPemFile(caCertPath.toFile());

    // 4. Verifikuj da je sertifikatista potpisan od našeg CA
    userCert.verify(caCert.getPublicKey());

    // 5. STROGA PROVJERA IDENTITY-ja: Ekstrakcija CN-a iz sertifikata i poređenje sa username-om
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

  /**
   * Konvertuje PEM String privatnog ključa u PrivateKey objekat
   */
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

  /**
   * Učitava privatni ključ direktno iz .p12 fajla preko lozinke
   */
  public PrivateKey loadPrivateKeyFromP12(File p12File, String alias, String password) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
    try (FileInputStream fis = new FileInputStream(p12File)) {
      keyStore.load(fis, password.toCharArray());
    }
    return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
  }
}