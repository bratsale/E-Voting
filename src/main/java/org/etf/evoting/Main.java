package org.etf.evoting;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.etf.evoting.pki.manager.KeyManager;
import org.etf.evoting.pki.model.RootCA;
import org.etf.evoting.pki.model.SubordinateCA;

import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;

public class Main {
  public static void main(String[] args) {
    // Registracija Bouncy Castle provajdera
    Security.addProvider(new BouncyCastleProvider());

    try {
      System.out.println("[*] Inicijalizacija PKI sistema...");

      // 1. Inicijalizacija Root CA (Učitava postojeći ili kreira novi ako ga nema)
      String rootDN = "CN=ETF Evoting Root CA, O=ETF Banja Luka, C=BA";
      RootCA rootCA = new RootCA("pki/root-ca", rootDN);
      System.out.println("[✓] Root CA je spreman (objekat inicijalizovan).");

      // 2. Inicijalizacija podređenih CA tijela preko Root CA objekta
      String orgDN = "CN=ETF Evoting Organizacioni CA, O=ETF Banja Luka, C=BA";
      SubordinateCA organizacioniCA = rootCA.createSubordinateCA(
          "pki/organizacioni-ca", orgDN, "organizacioni", "organizacioni");
      System.out.println("[✓] Organizacioni CA je spreman.");

      String glasackiDN = "CN=ETF Evoting Glasacki CA, O=ETF Banja Luka, C=BA";
      SubordinateCA glasackiCA = rootCA.createSubordinateCA(
          "pki/glasacki-ca", glasackiDN, "glasacki", "glasacki");
      System.out.println("[✓] Glasacki CA je spreman.\n");

      // ==========================================
      // SIMULACIJA IZDAVANJA KORISNIČKIH SERTIFIKATA
      // ==========================================
      System.out.println("[*] Simulacija registracije krajnjih korisnika...");

      // Registracija novog organizatora (npr. Sasa)
      System.out.println("[+] Kreiranje profila za organizatora [Sasa]...");
      KeyPair sasaKeyPair = KeyManager.generateRSAKeyPair(2048);
      String sasaDN = "CN=Sasa Organizator, OU=Uprava, O=ETF Banja Luka, C=BA";

      // Izdavanje vrši isključivo Organizacioni CA objekat
      X509Certificate sasaCert = organizacioniCA.issueUserCertificate(sasaKeyPair, sasaDN);
      KeyManager.saveToPEM(sasaKeyPair.getPrivate(), "pki/korisnici/organizatori", "sasa.key");
      KeyManager.saveToPEM(sasaCert, "pki/korisnici/organizatori", "sasa.crt");
      System.out.println("[✓] Sertifikat za [Sasa] uspjesno izdat.");

      // Registracija novog glasača (npr. Marko)
      System.out.println("[+] Kreiranje profila za glasaca [Marko]...");
      KeyPair markoKeyPair = KeyManager.generateRSAKeyPair(2048);
      String markoDN = "UID=indeks-123/23, CN=Marko Glasac, OU=Studenti, O=ETF Banja Luka, C=BA";

      // Izdavanje vrši isključivo Glasački CA objekat
      X509Certificate markoCert = glasackiCA.issueUserCertificate(markoKeyPair, markoDN);
      KeyManager.saveToPEM(markoKeyPair.getPrivate(), "pki/korisnici/glasaci", "marko.key");
      KeyManager.saveToPEM(markoCert, "pki/korisnici/glasaci", "marko.crt");
      System.out.println("[✓] Sertifikat za [Marko] uspjesno izdat.");

      System.out.println("\n[✓] REFRAKTORISANI PKI SISTEM RADI BESPREKORNO!");

    } catch (Exception e) {
      System.err.println("[-] Doslo je do greske u novom PKI sistemu:");
      e.printStackTrace();
    }
  }
}
