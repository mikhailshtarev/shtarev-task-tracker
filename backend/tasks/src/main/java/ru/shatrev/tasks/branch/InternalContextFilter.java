package ru.shatrev.tasks.branch;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class InternalContextFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = InternalContextFilter.class.getName() + ".userId";
    private static final String ERROR = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\",\"details\":null}}";
    private final byte[] key;

    public InternalContextFilter(@Value("${tasks.internal.hmac-key}") String encodedKey) {
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TASKS_INTERNAL_HMAC_KEY must be Base64", e);
        }
        if (key.length < 32) throw new IllegalStateException("TASKS_INTERNAL_HMAC_KEY must contain at least 32 bytes");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID userId = verify(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(ERROR);
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        chain.doFilter(request, response);
    }

    private UUID verify(HttpServletRequest request) {
        String user = one(request, "X-Internal-User-Id");
        String time = one(request, "X-Internal-Auth-Time");
        String signature = one(request, "X-Internal-Auth-Signature");
        if (user == null || time == null || signature == null) return null;
        try {
            UUID id = UUID.fromString(user);
            if (!id.toString().equals(user) || !time.matches("0|[1-9][0-9]*")) return null;
            long seconds = Long.parseLong(time);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 30 || seconds > now + 30) return null;
            byte[] supplied = Base64.getUrlDecoder().decode(signature);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(supplied).equals(signature)) return null;
            String message = "v1\ntasks\n" + user + "\n" + time + "\n" + request.getMethod() + "\n" + request.getRequestURI();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, supplied) ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String one(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
