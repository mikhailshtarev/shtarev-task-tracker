package ru.shatrev.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.shatrev.auth.exception.ErrorResponse;
import ru.shatrev.auth.security.JwtAuthenticationFilter;
import ru.shatrev.auth.security.OriginCheckFilter;

import java.util.List;

/**
 * Правила доступа (раздел 6.5): публичные эндпоинты, /me и /change-password — только Bearer,
 * stateless-сессии, CSRF отключён (защита cookie-эндпоинтов — через OriginCheckFilter).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/confirm",
            "/api/v1/auth/resend-confirmation",
            "/api/v1/auth/google",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/.well-known/jwks.json"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OriginCheckFilter originCheckFilter;
    private final CorsConfig corsConfig;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          OriginCheckFilter originCheckFilter,
                          CorsConfig corsConfig) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.originCheckFilter = originCheckFilter;
        this.corsConfig = corsConfig;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(json(ErrorResponse.of("UNAUTHORIZED", "Требуется авторизация", null)));
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(json(ErrorResponse.of("FORBIDDEN", "Доступ запрещён", null)));
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Origin-проверка должна выполняться до CORS-фильтра Spring Security,
     * чтобы ответ с недопустимым Origin был в едином формате ошибок (ORIGIN_NOT_ALLOWED).
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<OriginCheckFilter> originCheckFilterRegistration(
            OriginCheckFilter originCheckFilter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(originCheckFilter);
        registration.addUrlPatterns("/api/v1/auth/*");
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsConfig.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String json(ErrorResponse body) {
        return "{\"error\":{\"code\":\"" + body.error().code() + "\",\"message\":"
                + quote(body.error().message()) + ",\"details\":null}}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
