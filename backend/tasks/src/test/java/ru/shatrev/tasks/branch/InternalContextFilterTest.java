package ru.shatrev.tasks.branch;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InternalContextFilterTest {
    private static final byte[] KEY = new byte[32];
    private final InternalContextFilter filter = new InternalContextFilter(Base64.getEncoder().encodeToString(KEY));

    @Test
    void refusesMissingAndTamperedContext() throws Exception {
        var absent = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/branches"), absent, new MockFilterChain());
        assertEquals(401, absent.getStatus());

        var request = signedRequest();
        request.setRequestURI("/api/v1/branches/other");
        var changed = new MockHttpServletResponse();
        filter.doFilter(request, changed, new MockFilterChain());
        assertEquals(401, changed.getStatus());

        var methodChanged = signedRequest();
        methodChanged.setMethod("POST");
        var methodResponse = new MockHttpServletResponse();
        filter.doFilter(methodChanged, methodResponse, new MockFilterChain());
        assertEquals(401, methodResponse.getStatus());

        var expired = signedRequest();
        expired.removeHeader("X-Internal-Auth-Time");
        expired.addHeader("X-Internal-Auth-Time", Long.toString(Instant.now().minusSeconds(60).getEpochSecond()));
        var expiredResponse = new MockHttpServletResponse();
        filter.doFilter(expired, expiredResponse, new MockFilterChain());
        assertEquals(401, expiredResponse.getStatus());
    }

    @Test
    void acceptsAuthenticContextAndRejectsDuplicateHeader() throws Exception {
        var request = signedRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertInstanceOf(UUID.class, request.getAttribute(InternalContextFilter.USER_ID_ATTRIBUTE));

        var duplicate = signedRequest();
        duplicate.addHeader("X-Internal-User-Id", UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();
        filter.doFilter(duplicate, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    private static MockHttpServletRequest signedRequest() throws Exception {
        UUID user = UUID.randomUUID();
        String time = Long.toString(Instant.now().getEpochSecond());
        String payload = "v1\ntasks\n" + user + "\n" + time + "\nGET\n/api/v1/branches";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        var request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Internal-User-Id", user.toString());
        request.addHeader("X-Internal-Auth-Time", time);
        request.addHeader("X-Internal-Auth-Signature", signature);
        return request;
    }
}
