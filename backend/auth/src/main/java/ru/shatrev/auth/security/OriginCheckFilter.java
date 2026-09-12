package ru.shatrev.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.shatrev.auth.config.CorsConfig;
import ru.shatrev.auth.exception.ErrorResponse;

import java.io.IOException;
import java.util.List;

/** CSRF-защита cookie-эндпоинтов: проверка заголовка Origin против whitelist (раздел 6.6). */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PATHS = List.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final CorsConfig corsConfig;

    public OriginCheckFilter(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && PROTECTED_PATHS.contains(request.getRequestURI())) {
            String origin = request.getHeader("Origin");
            // Отклоняем только запросы с присутствующим Origin вне whitelist (раздел 6.6)
            if (origin != null && !corsConfig.isOriginAllowed(origin)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        """
                        {"error":{"code":"ORIGIN_NOT_ALLOWED","message":"Запрос с этого Origin не разрешён","details":null}}"""
                );
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
