package org.etf.evoting;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.etf.evoting.pki.PKIUtils;

import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;

public class Main {
  public static void main(String[] args) {
    // Registracija Bouncy Castle provajdera
    Security.addProvider(new BouncyCastleProvider());

    try {
      System.out.println("[*] Pokretanje generisanja kompletne CA infrastrukture...");

      // ==========================================
      // 1. KREIRANJE ROOT CA
      // ==========================================
      KeyPair rootKeyPair = PKIUtils.generateRSAKeyPair(4096); // 4096 bita za krovni CA
      String rootDN = "CN=ETF Evoting Root CA, O=ETF Banja Luka, C=BA";
      X509Certificate rootCert = PKIUtils.createRootCACertificate(rootKeyPair, rootDN);

      PKIUtils.saveToPEM(rootKeyPair.getPrivate(), "pki/root-ca", "root.key");
      PKIUtils.saveToPEM(rootCert, "pki/root-ca", "root.crt");
      System.out.println("[✓] Root CA uspjesno kreiran i sacuvan.");

      // ==========================================
      // 2. KREIRANJE ORGANIZACIONOG CA
      // ==========================================
      KeyPair orgCAKeyPair = PKIUtils.generateRSAKeyPair(2048); // 2048 bita je sasvim dovoljno za sub-CA
      String orgCADN = "CN=ETF Evoting Organizacioni CA, O=ETF Banja Luka, C=BA";
      X509Certificate orgCACert = PKIUtils.createSubordinateCACertificate(
          orgCAKeyPair, orgCADN, rootCert, rootKeyPair.getPrivate());

      PKIUtils.saveToPEM(orgCAKeyPair.getPrivate(), "pki/organizacioni-ca", "organizacioni.key");
      PKIUtils.saveToPEM(orgCACert, "pki/organizacioni-ca", "organizacioni.crt");
      System.out.println("[✓] Organizacioni CA kreiran i potpisan od strane Root CA.");

      // ==========================================
      // 3. KREIRANJE GLASAČKOG CA
      // ==========================================
      KeyPair glasackiCAKeyPair = PKIUtils.generateRSAKeyPair(2048);
      String glasackiCADN = "CN=ETF Evoting Glasacki CA, O=ETF Banja Luka, C=BA";
      X509Certificate glasackiCACert = PKIUtils.createSubordinateCACertificate(
          glasackiCAKeyPair, glasackiCADN, rootCert, rootKeyPair.getPrivate());

      PKIUtils.saveToPEM(glasackiCAKeyPair.getPrivate(), "pki/glasacki-ca", "glasacki.key");
      PKIUtils.saveToPEM(glasackiCACert, "pki/glasacki-ca", "glasacki.crt");
      System.out.println("[✓] Glasacki CA kreiran i potpisan od strane Root CA.");

      System.out.println("\n[✓] SVE BOMBA! Kompletna PKI hijerarhija je na disku.");

    } catch (Exception e) {
      System.err.println("[-] Doslo je do greske prilikom kreiranja PKI strukture:");
      e.printStackTrace();
    }
  }
}
