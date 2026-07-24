package org.etf.evoting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        String username = null;
        String jwtToken = null;

        // 1. Provjera da li postoji Bearer token u zaglavlju
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwtToken);
            } catch (Exception e) {
                // Token nevalidan, istekao ili loše formatiran
                logger.error("Neuspješno ekstrakovanje korisničkog imena iz JWT tokena: " + e.getMessage());
            }
        }

        // 2. Ako je username ekstrahovan i korisnik nije već autentifikovan u SecurityContext-u
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            if (!jwtUtil.isTokenExpired(jwtToken)) {
                String role = jwtUtil.extractRole(jwtToken);

                // Spring Security očekuje role sa prefiksom "ROLE_"
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(authority)
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Postavljamo autentifikovanog korisnika u kontekst
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}