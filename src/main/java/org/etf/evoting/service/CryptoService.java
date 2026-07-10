package org.etf.evoting.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
public class CryptoService {

  /**
   * Pretvara PEM string sertifikata iz baze u X509Certificate objekat.
   */
  public X509Certificate convertPemToCertificate(String certificatePem) {
    try {
      // Očišćavanje stringa ako klijent pošalje sa whitespace-ovima
      String cleanPem = certificatePem
          .replace("-----BEGIN CERTIFICATE-----", "")
          .replace("-----END CERTIFICATE-----", "")
          .replaceAll("\\s+", "");

      byte[] decoded = Base64.getDecoder().decode(cleanPem);
      CertificateFactory factory = CertificateFactory.getInstance("X.509");

      return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
    } catch (Exception e) {
      throw new IllegalArgumentException("Nevalidan X.509 sertifikat u PEM formatu.", e);
    }
  }

  /**
   * Verifikuje da li je podatak (npr. ID opcije ili tekst glasa) zaista potpisan
   * odgovarajućim privatnim ključem koji se poklapa sa javnim ključem iz
   * sertifikata.
   * 
   * @param certificatePem  PEM format sertifikata korisnika
   * @param data            Podaci koji su potpisani (npr. "electionId:optionId")
   * @param signatureBase64 Digitalni potpis u Base64 formatu poslat sa klijenta
   */
  public boolean verifySignature(String certificatePem, String data, String signatureBase64) {
    try {
      // 1. Izvuci sertifikat i javni ključ
      X509Certificate certificate = convertPemToCertificate(certificatePem);

      // Opciono: Ovdje se može dodati i provjera roka važenja sertifikata
      certificate.checkValidity();

      PublicKey publicKey = certificate.getPublicKey();

      // 2. Inicijalizuj Signature objekat (Pretpostavljamo SHA256withRSA)
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);

      // 3. Ubaci podatke koji su potpisani
      signature.update(data.getBytes(StandardCharsets.UTF_8));

      // 4. Dekodiraj potpis i verifikuj
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      return signature.verify(signatureBytes);

    } catch (Exception e) {
      // Ako išta pukne tokom verifikacije (loš format, istekao sertifikat...), potpis
      // nije validan
      return false;
    }
  }
}
