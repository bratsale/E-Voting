package org.etf.evoting;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.etf.evoting.pki.PKIUtils;

import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;

public class Main {
  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleProvider());

    try {
      System.out.println("[*] KORAK 1: Generisanje krovne CA infrastrukture...");

      // Root CA
      KeyPair rootKeyPair = PKIUtils.generateRSAKeyPair(4096);
      String rootDN = "CN=ETF Evoting Root CA, O=ETF Banja Luka, C=BA";
      X509Certificate rootCert = PKIUtils.createRootCACertificate(rootKeyPair, rootDN);
      PKIUtils.saveToPEM(rootKeyPair.getPrivate(), "pki/root-ca", "root.key");
      PKIUtils.saveToPEM(rootCert, "pki/root-ca", "root.crt");

      // Organizacioni CA
      KeyPair orgCAKeyPair = PKIUtils.generateRSAKeyPair(2048);
      String orgCADN = "CN=ETF Evoting Organizacioni CA, O=ETF Banja Luka, C=BA";
      X509Certificate orgCACert = PKIUtils.createSubordinateCACertificate(
          orgCAKeyPair, orgCADN, rootCert, rootKeyPair.getPrivate());
      PKIUtils.saveToPEM(orgCAKeyPair.getPrivate(), "pki/organizacioni-ca", "organizacioni.key");
      PKIUtils.saveToPEM(orgCACert, "pki/organizacioni-ca", "organizacioni.crt");

      // Glasacki CA
      KeyPair glasackiCAKeyPair = PKIUtils.generateRSAKeyPair(2048);
      String glasackiCADN = "CN=ETF Evoting Glasacki CA, O=ETF Banja Luka, C=BA";
      X509Certificate glasackiCACert = PKIUtils.createSubordinateCACertificate(
          glasackiCAKeyPair, glasackiCADN, rootCert, rootKeyPair.getPrivate());
      PKIUtils.saveToPEM(glasackiCAKeyPair.getPrivate(), "pki/glasacki-ca", "glasacki.key");
      PKIUtils.saveToPEM(glasackiCACert, "pki/glasacki-ca", "glasacki.crt");

      System.out.println("[✓] CA infrastruktura je spremna.\n");

      // ==========================================
      // KORAK 2: SIMULACIJA REGISTRACIJE KORISNIKA
      // ==========================================
      System.out.println("[*] KORAK 2: Registracija korisnika i izdavanje sertifikata...");

      // 1. Registracija Organizatora (npr. Sasa)
      System.out.println("[+] Generisanje kljuceva za organizatora Sasa...");
      KeyPair organizatorKeyPair = PKIUtils.generateRSAKeyPair(2048); // Korisnički ključevi su 2048 bita
      String organizatorDN = "CN=Sasa Organizator, OU=Administracija, O=ETF Banja Luka, C=BA";

      // Organizacioni CA potpisuje Sasin sertifikat
      X509Certificate organizatorCert = PKIUtils.createUserCertificate(
          organizatorKeyPair, organizatorDN, orgCACert, orgCAKeyPair.getPrivate());

      // Čuvamo Sasin par u poseban folder za korisnike
      PKIUtils.saveToPEM(organizatorKeyPair.getPrivate(), "pki/korisnici/organizatori", "sasa.key");
      PKIUtils.saveToPEM(organizatorCert, "pki/korisnici/organizatori", "sasa.crt");
      System.out.println("[✓] Sertifikat za organizatora [Sasa] uspjesno izdat od strane Organizacionog CA.");

      // 2. Registracija Glasača (npr. Marko)
      System.out.println("[+] Generisanje kljuceva za glasaca Marko...");
      KeyPair glasacKeyPair = PKIUtils.generateRSAKeyPair(2048);
      String glasacDN = "UID=123456, CN=Marko Glasac, OU=Studenti, O=ETF Banja Luka, C=BA"; // UID može biti broj
                                                                                            // indeksa

      // Glasački CA potpisuje Markov sertifikat
      X509Certificate glasacCert = PKIUtils.createUserCertificate(
          glasacKeyPair, glasacDN, glasackiCACert, glasackiCAKeyPair.getPrivate());

      // Čuvamo Markov par
      PKIUtils.saveToPEM(glasacKeyPair.getPrivate(), "pki/korisnici/glasaci", "marko.key");
      PKIUtils.saveToPEM(glasacCert, "pki/korisnici/glasaci", "marko.crt");
      System.out.println("[✓] Sertifikat za glasaca [Marko] uspjesno izdat od strane Glasackog CA.");

      System.out.println("\n[✓] SVE JE PROŠLO TOP! Korisnicki sertifikati su sacuvani na disk.");

    } catch (Exception e) {
      System.err.println("[-] Greska tokom end-to-end testa:");
      e.printStackTrace();
    }
  }
}
