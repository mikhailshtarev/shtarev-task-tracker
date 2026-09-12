package ru.shatrev.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.shatrev.auth.exception.ApiException;
import ru.shatrev.auth.service.JwtService;

import java.io.IOException;
import java.util.UUID;

/** Валидация Bearer access-токена для защищённых эндпоинтов (/me, /change-password и др.). */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            var claims = jwtService.validateToken(token);
            if (claims == null) {
                // Неверный токен: контекст пуст, Security вернёт 401 через AuthenticationEntryPoint.
                SecurityContextHolder.clearContext();
            } else if (JwtService.TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
                UUID userId = UUID.fromString(claims.getSubject());
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    /** UUID текущего пользователя из SecurityContext (principal — id из access-токена). */
    public static UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw ApiException.unauthorized();
        }
        return userId;
    }
}
