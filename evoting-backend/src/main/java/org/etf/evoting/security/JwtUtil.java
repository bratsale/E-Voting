package org.etf.evoting.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // Generisanje tajnog ključa za potpisivanje JWT tokena (u produkciji se vuče iz application.properties)
    private final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // Trajanje tokena: 24 sata
    private final long EXPIRATION_TIME = 86400000;

    /**
     * Generisanje tokena za zadatog korisnika sa njegovom ulogom i ID-jem.
     */
    public String generateToken(String username, Integer userId, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Izvlačenje korisničkog imena iz tokena.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Izvlačenje uloge korisnika iz tokena.
     */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Izvlačenje ID-ja korisnika iz tokena.
     */
    public Integer extractUserId(String token) {
        return extractAllClaims(token).get("userId", Integer.class);
    }

    /**
     * Provjera da li je token istekao.
     */
    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Validacija tokena.
     */
    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}