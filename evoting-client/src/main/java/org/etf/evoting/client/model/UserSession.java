package org.etf.evoting.client.model;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class UserSession {
    private static UserSession instance;

    private String username;
    private String token;
    private String role;
    private PrivateKey privateKey;
    private X509Certificate certificate;

    private UserSession() {}

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void cleanUserSession() {
        username = null;
        token = null;
        role = null;
        privateKey = null;
        certificate = null;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public PrivateKey getPrivateKey() { return privateKey; }
    public void setPrivateKey(PrivateKey privateKey) { this.privateKey = privateKey; }

    public X509Certificate getCertificate() { return certificate; }
    public void setCertificate(X509Certificate certificate) { this.certificate = certificate; }
}