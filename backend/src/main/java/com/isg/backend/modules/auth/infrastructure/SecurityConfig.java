package com.isg.backend.modules.auth.infrastructure;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public RestSecurityErrorHandler restSecurityErrorHandler(
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new RestSecurityErrorHandler(objectMapper, clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            AuthenticationProvider authenticationProvider,
            RestSecurityErrorHandler restSecurityErrorHandler) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restSecurityErrorHandler)
                        .accessDeniedHandler(restSecurityErrorHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // 0. CORS Preflight (OPTIONS) isteklerine izin ver
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 1. Public Auth Endpointleri (Tüm auth yolları serbest bırakıldı)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Health endpoint'ini public yapıyoruz
                        .requestMatchers("/actuator/health").permitAll()

                        // BE-3: WebSocket bağlantısına izin verilir; kimlik doğrulama STOMP CONNECT aşamasında JWT ile yapılır.
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // 2. Internal Endpointler (Gateway / AI Worker için)
                        .requestMatchers("/internal/v1/**").permitAll()

                        // 3. User Uç Noktaları
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/departments").authenticated()
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                        // 3.1. Kamera Yönetim Uç Noktaları (Mutasyonlar ADMIN'e özel, listeleme/detay rollerine açık)
                        .requestMatchers(HttpMethod.POST, "/api/v1/cameras/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cameras/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/cameras/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/cameras/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cameras/**").hasAnyRole("ADMIN", "OHS_SPECIALIST", "SHIFT_SUPERVISOR")

                        // 4. Dashboard Endpointleri (Görev planındaki yetkili roller)
                        .requestMatchers("/api/v1/dashboard/**").hasAnyRole("ADMIN", "OHS_SPECIALIST", "SHIFT_SUPERVISOR")

                        // 5. Kalan tüm istekler JWT token gerektirir
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Internal-Api-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}