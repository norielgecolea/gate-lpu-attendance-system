package org.nors.dev.codes.lpu.config;

import java.util.List;
import org.nors.dev.codes.lpu.security.JwtAuthEntryPoint;
import org.nors.dev.codes.lpu.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtAuthEntryPoint jwtAuthEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/pictures/**").permitAll()
                        // <video> tags cannot attach auth headers
                        .requestMatchers(HttpMethod.GET, "/videos/**").permitAll()
                        // <audio> tags cannot attach auth headers
                        .requestMatchers(HttpMethod.GET, "/tones/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Any signed-in role (Superadmin, OSAS, HR, Monitoring, Guard) can manage its own session.
                        .requestMatchers("/api/auth/**").authenticated()
                        // Guard kiosks read the display setting; OSAS admins manage it.
                        .requestMatchers(HttpMethod.GET, "/api/guard-display")
                        .hasAnyRole("SUPERADMIN", "GUARD", "OSAS")
                        .requestMatchers("/api/guard-display/**")
                        .hasAnyRole("SUPERADMIN", "OSAS")
                        // Guard kiosks load assigned tones; OSAS/superadmin manage the library.
                        .requestMatchers(HttpMethod.GET, "/api/gate-tones")
                        .hasAnyRole("SUPERADMIN", "GUARD", "OSAS")
                        .requestMatchers("/api/gate-tones/**")
                        .hasAnyRole("SUPERADMIN", "OSAS")
                        // Dashboard read access for cross-role summaries.
                        .requestMatchers(HttpMethod.GET, "/api/students/**")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        .requestMatchers("/api/students/**")
                        .hasAnyRole("SUPERADMIN", "OSAS")
                        .requestMatchers(HttpMethod.GET, "/api/employees/**")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        .requestMatchers("/api/employees/**")
                        .hasAnyRole("SUPERADMIN", "HR")
                        .requestMatchers(HttpMethod.GET, "/api/rfid/**")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        .requestMatchers("/api/users/**")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        .requestMatchers(HttpMethod.GET, "/api/tap-errors/count")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR", "MONITORING")
                        .requestMatchers("/api/tap-errors/**")
                        .hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        // Kiosk endpoints stay available to guards; reporting is admin/monitoring.
                        .requestMatchers("/api/attendance/tap").hasAnyRole("SUPERADMIN", "GUARD")
                        .requestMatchers("/api/attendance/recent")
                        .hasAnyRole("SUPERADMIN", "GUARD", "MONITORING", "OSAS", "HR")
                        // Live guard kiosk presence for dashboard / monitoring wall.
                        .requestMatchers(HttpMethod.GET, "/api/guards/online")
                        .hasAnyRole("SUPERADMIN", "MONITORING", "OSAS", "HR")
                        // Read-only aggregates power the monitoring wall and admin dashboards.
                        .requestMatchers(
                                "/api/attendance/summary",
                                "/api/attendance/by-department",
                                "/api/attendance/by-hour"
                        )
                        .hasAnyRole("SUPERADMIN", "MONITORING", "OSAS", "HR")
                        .requestMatchers("/api/attendance/**").hasAnyRole("SUPERADMIN", "OSAS", "HR")
                        .requestMatchers("/api/**").hasRole("SUPERADMIN")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Origin has no path — trailing /* would reject browser CORS and return 403
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://rfidattendance.lpulaguna.com",
                "https://*.lpulaguna.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
