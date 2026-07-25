package org.etf.evoting.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Javni endpoint-i (Registracija i Prijava)
                        .requestMatchers("/api/auth/**").permitAll()

                        // 2. Zaštita rute za kreiranje i upravljanje izborima (Samo ORGANIZER)
                        .requestMatchers("/api/elections/create").hasRole("ORGANIZER")
                        .requestMatchers("/api/elections/{id}/activate").hasRole("ORGANIZER")
                        .requestMatchers("/api/elections/{id}/finish").hasRole("ORGANIZER")

                        // 3. Pregled izbora i glasanje
                        .requestMatchers("/api/elections/active").authenticated()
                        .requestMatchers("/api/elections/{id}/results").authenticated()
                        .requestMatchers("/api/voting/cast").hasAnyAuthority("VOTER", "ROLE_VOTER", "USER")

                        // Za sve ostalo tražimo autentifikaciju
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}