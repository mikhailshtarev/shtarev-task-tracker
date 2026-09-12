package ru.shatrev.auth.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import ru.shatrev.auth.service.GoogleOAuth2Service;
import ru.shatrev.auth.support.AbstractAuthIntegrationTest;
import ru.shatrev.auth.support.HttpTestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Интеграционные сценарии B-08…B-28 (раздел 7 плана backend-auth). */
@Tag("integration")
@EnabledIf(value = "ru.shatrev.auth.support.DockerCheck#isDockerAvailable",
        disabledReason = "Docker недоступен — интеграционные тесты требуют Testcontainers")
class AuthEndpointsIntegrationTest extends AbstractAuthIntegrationTest {

    private String uniqueIp() {
        return "10." + (int) (Math.random() * 250 + 1) + "." + (int) (Math.random() * 250 + 1)
                + "." + (int) (Math.random() * 250 + 1);
    }

    // ---------- B-08 ----------

    @Test
    void b08_flywayCreatesSixTablesAndIndexes() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "password_history", "refresh_token_blacklist",
                "email_confirmation_tokens", "password_reset_tokens", "login_attempts");

        List<String> indexNames = List.of("idx_password_history_user", "idx_refresh_blacklist_jti",
                "idx_refresh_blacklist_user", "idx_email_confirmation_token", "idx_email_confirmation_user",
                "idx_password_reset_token", "idx_password_reset_user", "idx_login_attempts_user",
                "idx_login_attempts_ip", "idx_login_attempts_endpoint_ip");
        for (String indexName : indexNames) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from pg_indexes where indexname = ?", Integer.class, indexName);
            assertThat(count).as("index %s", indexName).isEqualTo(1);
        }
    }

    // ---------- B-09 ----------

    @Test
    void b09_registerSuccessThenConflictThenValidationError() {
        String ip = uniqueIp();
        String email = "b09-" + UUID.randomUUID() + "@example.com";

        HttpTestClient.SimpleResponse first = register(email, PASSWORD, ip);
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body()).contains("Проверьте email для подтверждения");

        HttpTestClient.SimpleResponse duplicate = register(email, PASSWORD, ip);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(errorCode(duplicate)).isEqualTo("EMAIL_ALREADY_EXISTS");

        HttpTestClient.SimpleResponse invalid =
                register("b09b-" + UUID.randomUUID() + "@example.com", "weak", ip);
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(errorCode(invalid)).isEqualTo("VALIDATION_ERROR");
        assertThat(invalid.body()).contains("\"details\"");
    }

    // ---------- B-10 ----------

    @Test
    void b10_confirmEmailSuccessRepeatAndExpired() {
        String ip = uniqueIp();
        String email = "b10-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);

        HttpTestClient.SimpleResponse confirmed = get("/api/v1/auth/confirm?token=" + token, Map.of());
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.body()).contains("Email подтверждён");

        Boolean isConfirmed = jdbc.queryForObject("select is_confirmed from users where email = ?",
                Boolean.class, email);
        assertThat(isConfirmed).isTrue();

        Integer tokenCount = jdbc.queryForObject(
                "select count(*) from email_confirmation_tokens ect join users u on u.id = ect.user_id where u.email = ?",
                Integer.class, email);
        assertThat(tokenCount).isZero();

        HttpTestClient.SimpleResponse repeated = get("/api/v1/auth/confirm?token=" + token, Map.of());
        assertThat(repeated.status()).isEqualTo(400);
        assertThat(errorCode(repeated)).isEqualTo("INVALID_TOKEN");

        String expiredToken = registerAndCaptureToken("b10e-" + UUID.randomUUID() + "@example.com", uniqueIp());
        jdbc.update("update email_confirmation_tokens set expires_at = now() - interval '25 hours'");
        HttpTestClient.SimpleResponse expired = get("/api/v1/auth/confirm?token=" + expiredToken, Map.of());
        assertThat(expired.status()).isEqualTo(400);
        assertThat(errorCode(expired)).isEqualTo("INVALID_TOKEN");
    }

    // ---------- B-11 ----------

    @Test
    void b11_resendConfirmationDoesNotRevealStatusAndIsRateLimited() {
        String ip = uniqueIp();
        String unknownEmail = "b11-" + UUID.randomUUID() + "@example.com";

        for (int i = 0; i < 3; i++) {
            HttpTestClient.SimpleResponse response = post("/api/v1/auth/resend-confirmation",
                    Map.of("email", unknownEmail), headersWithIp(ip));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).contains("Если email зарегистрирован");
        }

        HttpTestClient.SimpleResponse fourth = post("/api/v1/auth/resend-confirmation",
                Map.of("email", unknownEmail), headersWithIp(ip));
        assertThat(fourth.status()).isEqualTo(429);
        assertThat(errorCode(fourth)).isEqualTo("RATE_LIMITED");
    }

    // ---------- B-12 ----------

    @Test
    void b12_loginReturnsAccessTokenAndHttpOnlyCookieAndUpdatesLastLogin() {
        String ip = uniqueIp();
        String email = "b12-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());

        HttpTestClient.SimpleResponse response = login(email, PASSWORD, ip);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("accessToken");

        String setCookie = response.cookieValue("refreshToken");
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/").contains("Max-Age=604800");

        LocalDateTime lastLogin = jdbc.queryForObject("select last_login_at from users where email = ?",
                LocalDateTime.class, email);
        assertThat(lastLogin).isNotNull();
    }

    // ---------- B-13 ----------

    @Test
    void b13_loginUnknownEmailAndWrongPasswordAreIndistinguishable() {
        String ip = uniqueIp();
        String email = "b13-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());

        HttpTestClient.SimpleResponse wrongPassword = login(email, "WrongPass9", ip);
        HttpTestClient.SimpleResponse unknownEmail =
                login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD, ip);

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(errorCode(wrongPassword)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
    }

    // ---------- B-14 ----------

    @Test
    void b14_loginUnconfirmedEmailIsRejected() {
        String ip = uniqueIp();
        String email = "b14-" + UUID.randomUUID() + "@example.com";
        registerAndCaptureToken(email, ip);

        HttpTestClient.SimpleResponse response = login(email, PASSWORD, ip);
        assertThat(response.status()).isEqualTo(401);
        assertThat(errorCode(response)).isEqualTo("EMAIL_NOT_CONFIRMED");
    }

    // ---------- B-15 ----------

    @Test
    void b15_userBlockedAfterFiveFailedLoginsEvenWithCorrectPassword() {
        String ip = uniqueIp();
        String email = "b15-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());

        for (int i = 0; i < 5; i++) {
            login(email, "WrongPass9", ip);
        }
        HttpTestClient.SimpleResponse sixth = login(email, PASSWORD, ip);
        assertThat(sixth.status()).isEqualTo(429);
        assertThat(errorCode(sixth)).isEqualTo("RATE_LIMITED");
    }

    // ---------- B-16 ----------

    @Test
    void b16_ipBlockedAfterTwentyFailedLoginsIncludingUnknownEmails() {
        String ip = uniqueIp();
        String email = "b16-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());

        for (int i = 0; i < 20; i++) {
            login("ghost-" + i + "-" + UUID.randomUUID() + "@example.com", PASSWORD, ip);
        }

        HttpTestClient.SimpleResponse blocked = login(email, PASSWORD, ip);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(errorCode(blocked)).isEqualTo("RATE_LIMITED");
    }

    // ---------- B-17 ----------

    @Test
    void b17_refreshIsSingleUse() {
        String ip = uniqueIp();
        String email = "b17-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);

        Map<String, String> cookie = cookie(Map.of(), loginResponse.cookieValue("refreshToken"));
        HttpTestClient.SimpleResponse refresh = postEmpty("/api/v1/auth/refresh", cookie);
        assertThat(refresh.status()).isEqualTo(200);
        assertThat(refresh.body()).contains("accessToken");
        assertThat(refresh.cookieValue("refreshToken")).isNotBlank();

        HttpTestClient.SimpleResponse reuse = postEmpty("/api/v1/auth/refresh", cookie);
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(errorCode(reuse)).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    // ---------- B-18 ----------

    @Test
    void b18_parallelRefreshWithSameTokenOnlyOneSucceeds() throws Exception {
        String ip = uniqueIp();
        String email = "b18-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);
        Map<String, String> cookie = cookie(Map.of(), loginResponse.cookieValue("refreshToken"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpTestClient.SimpleResponse> task = () -> postEmpty("/api/v1/auth/refresh", cookie);
            Future<HttpTestClient.SimpleResponse> first = executor.submit(task);
            Future<HttpTestClient.SimpleResponse> second = executor.submit(task);

            int ok = 0;
            int unauthorized = 0;
            for (HttpTestClient.SimpleResponse response : List.of(first.get(), second.get())) {
                if (response.status() == 200) {
                    ok++;
                } else if (response.status() == 401) {
                    unauthorized++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(unauthorized).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------- B-19 ----------

    @Test
    void b19_refreshRejectedWhenIatBeforeTokensValidFrom() {
        String ip = uniqueIp();
        String email = "b19-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);
        Map<String, String> cookie = cookie(Map.of(), loginResponse.cookieValue("refreshToken"));

        // Смена/сброс пароля устанавливает tokens_valid_from = now()
        jdbc.update("update users set tokens_valid_from = now() where email = ?", email);

        HttpTestClient.SimpleResponse refresh = postEmpty("/api/v1/auth/refresh", cookie);
        assertThat(refresh.status()).isEqualTo(401);
        assertThat(errorCode(refresh)).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    // ---------- B-20 ----------

    @Test
    void b20_logoutBlacklistsRefreshTokenAndClearsCookie() {
        String ip = uniqueIp();
        String email = "b20-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);
        Map<String, String> cookie = cookie(Map.of(), loginResponse.cookieValue("refreshToken"));

        HttpTestClient.SimpleResponse logout = postEmpty("/api/v1/auth/logout", cookie);
        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.cookieValue("refreshToken")).contains("Max-Age=0");

        HttpTestClient.SimpleResponse refresh = postEmpty("/api/v1/auth/refresh", cookie);
        assertThat(refresh.status()).isEqualTo(401);
    }

    // ---------- B-21 ----------

    @Test
    void b21_changePasswordWrongCurrentRejected() {
        String ip = uniqueIp();
        String email = "b21-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        String accessToken = accessTokenOf(login(email, PASSWORD, ip));

        HttpTestClient.SimpleResponse wrongCurrent = post("/api/v1/auth/change-password",
                Map.of("currentPassword", "WrongPass9", "newPassword", "NewSecurePass2"), bearer(accessToken));
        assertThat(wrongCurrent.status()).isEqualTo(400);
        assertThat(errorCode(wrongCurrent)).isEqualTo("INVALID_CURRENT_PASSWORD");
    }

    @Test
    void b21_changePasswordTooRecentRejected() {
        String ip = uniqueIp();
        String email = "b21b-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        String accessToken = accessTokenOf(login(email, PASSWORD, ip));

        HttpTestClient.SimpleResponse response = post("/api/v1/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD), bearer(accessToken));
        assertThat(response.status()).isEqualTo(409);
        assertThat(errorCode(response)).isEqualTo("PASSWORD_TOO_RECENT");
    }

    @Test
    void b21_changePasswordSuccessInvalidatesOldRefreshTokens() {
        String ip = uniqueIp();
        String email = "b21c-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);
        String accessToken = accessTokenOf(loginResponse);
        Map<String, String> oldCookie = cookie(Map.of(), loginResponse.cookieValue("refreshToken"));

        HttpTestClient.SimpleResponse response = post("/api/v1/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", "NewSecurePass2"), bearer(accessToken));
        assertThat(response.status()).isEqualTo(200);

        // Старый refresh отозван (B-19: iat раньше tokens_valid_from)
        HttpTestClient.SimpleResponse refresh = postEmpty("/api/v1/auth/refresh", oldCookie);
        assertThat(refresh.status()).isEqualTo(401);

        // Вход со старым паролем больше не работает, с новым — работает
        assertThat(login(email, PASSWORD, uniqueIp()).status()).isEqualTo(401);
        assertThat(login(email, "NewSecurePass2", uniqueIp()).status()).isEqualTo(200);
    }

    // ---------- B-22 ----------

    @Test
    void b22_changePasswordRequiresBearer() {
        HttpTestClient.SimpleResponse withoutToken = post("/api/v1/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", "NewSecurePass2"), Map.of());
        assertThat(withoutToken.status()).isEqualTo(401);
        assertThat(errorCode(withoutToken)).isEqualTo("UNAUTHORIZED");

        HttpTestClient.SimpleResponse badToken = post("/api/v1/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", "NewSecurePass2"), bearer("not-a-jwt"));
        assertThat(badToken.status()).isEqualTo(401);
        assertThat(errorCode(badToken)).isEqualTo("UNAUTHORIZED");

        HttpTestClient.SimpleResponse me = get("/api/v1/auth/me", bearer("not-a-jwt"));
        assertThat(me.status()).isEqualTo(401);
        assertThat(errorCode(me)).isEqualTo("UNAUTHORIZED");
    }

    // ---------- B-23 ----------

    @Test
    void b23_googleCreatesUserThenAuthorizesThenConflicts() {
        String ip = uniqueIp();
        String googleEmail = "b23-" + UUID.randomUUID() + "@example.com";
        when(googleOAuth2Service.exchangeCodeForProfile("code-new"))
                .thenReturn(new GoogleOAuth2Service.GoogleProfile(googleEmail, "G User", "http://pic"));

        HttpTestClient.SimpleResponse first = post("/api/v1/auth/google", Map.of("code", "code-new"),
                headersWithIp(ip));
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body()).contains("accessToken");

        Map<String, Object> row = jdbc.queryForMap(
                "select password, is_confirmed, name from users where email = ?", googleEmail);
        assertThat(row.get("password")).isNull();
        assertThat((Boolean) row.get("is_confirmed")).isTrue();
        assertThat(row.get("name")).isEqualTo("G User");

        HttpTestClient.SimpleResponse second = post("/api/v1/auth/google", Map.of("code", "code-new"),
                headersWithIp(ip));
        assertThat(second.status()).isEqualTo(200);

        // Email занят пароль-аккаунтом
        String passwordEmail = "b23p-" + UUID.randomUUID() + "@example.com";
        register(passwordEmail, PASSWORD, uniqueIp());
        when(googleOAuth2Service.exchangeCodeForProfile("code-conflict"))
                .thenReturn(new GoogleOAuth2Service.GoogleProfile(passwordEmail, "G User", "http://pic"));
        HttpTestClient.SimpleResponse conflict = post("/api/v1/auth/google", Map.of("code", "code-conflict"),
                headersWithIp(ip));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(errorCode(conflict)).isEqualTo("EMAIL_CONFLICT");
    }

    // ---------- B-24 / B-25 ----------

    @Test
    void b24_forgotPasswordDoesNotRevealRegistrationAndCreatesToken() {
        String ip = uniqueIp();
        String unknownEmail = "b24-" + UUID.randomUUID() + "@example.com";
        HttpTestClient.SimpleResponse unknown = post("/api/v1/auth/forgot-password",
                Map.of("email", unknownEmail), headersWithIp(ip));
        assertThat(unknown.status()).isEqualTo(200);
        assertThat(unknown.body()).contains("Если email зарегистрирован");

        String email = "b24u-" + UUID.randomUUID() + "@example.com";
        String confirmToken = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + confirmToken, Map.of());
        post("/api/v1/auth/forgot-password", Map.of("email", email), headersWithIp(ip));

        LocalDateTime expiresAt = jdbc.queryForObject(
                "select prt.expires_at from password_reset_tokens prt join users u on u.id = prt.user_id where u.email = ?",
                LocalDateTime.class, email);
        assertThat(expiresAt).isBetween(LocalDateTime.now().plusMinutes(50), LocalDateTime.now().plusMinutes(70));
    }

    @Test
    void b25_resetPasswordFlow() {
        String ip = uniqueIp();
        String email = "b25-" + UUID.randomUUID() + "@example.com";
        String confirmToken = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + confirmToken, Map.of());
        post("/api/v1/auth/forgot-password", Map.of("email", email), headersWithIp(ip));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetPasswordEmail(eq(email), captor.capture());
        String resetToken = captor.getValue();

        HttpTestClient.SimpleResponse reset = post("/api/v1/auth/reset-password",
                Map.of("token", resetToken, "password", "NewSecurePass2"), headersWithIp(ip));
        assertThat(reset.status()).isEqualTo(200);

        assertThat(login(email, PASSWORD, uniqueIp()).status()).isEqualTo(401);
        assertThat(login(email, "NewSecurePass2", uniqueIp()).status()).isEqualTo(200);

        Boolean tokensValidFromSet = jdbc.queryForObject(
                "select tokens_valid_from is not null from users where email = ?", Boolean.class, email);
        assertThat(tokensValidFromSet).isTrue();

        Integer remainingTokens = jdbc.queryForObject(
                "select count(*) from password_reset_tokens prt join users u on u.id = prt.user_id where u.email = ?",
                Integer.class, email);
        assertThat(remainingTokens).isZero();

        HttpTestClient.SimpleResponse reused = post("/api/v1/auth/reset-password",
                Map.of("token", resetToken, "password", "ThirdSecure3"), headersWithIp(ip));
        assertThat(reused.status()).isEqualTo(400);
        assertThat(errorCode(reused)).isEqualTo("INVALID_TOKEN");
    }

    // ---------- B-26 ----------

    @Test
    void b26_refreshWithDisallowedOriginIs403() {
        String ip = uniqueIp();
        String email = "b26-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        HttpTestClient.SimpleResponse loginResponse = login(email, PASSWORD, ip);
        String refreshTokenCookie = loginResponse.cookieValue("refreshToken");

        Map<String, String> evil = new HashMap<>(cookie(Map.of(), refreshTokenCookie));
        evil.put("Origin", "https://evil.example.com");
        HttpTestClient.SimpleResponse forbidden = postEmpty("/api/v1/auth/refresh", evil);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(errorCode(forbidden)).isEqualTo("ORIGIN_NOT_ALLOWED");

        Map<String, String> allowed = new HashMap<>(cookie(Map.of(), refreshTokenCookie));
        allowed.put("Origin", ALLOWED_ORIGIN);
        HttpTestClient.SimpleResponse ok = postEmpty("/api/v1/auth/refresh", allowed);
        assertThat(ok.status()).isEqualTo(200);
    }

    // ---------- B-27 ----------

    @Test
    void b27_jwksPublishesKidMatchingIssuedTokens() {
        HttpTestClient.SimpleResponse jwks = get("/api/v1/auth/.well-known/jwks.json", Map.of());
        assertThat(jwks.status()).isEqualTo(200);
        assertThat(jwks.body()).contains("\"kid\":\"test-kid\"").contains("RS256")
                .contains("\"n\"").contains("\"e\"");

        String ip = uniqueIp();
        String email = "b27-" + UUID.randomUUID() + "@example.com";
        String token = registerAndCaptureToken(email, ip);
        get("/api/v1/auth/confirm?token=" + token, Map.of());
        String accessToken = accessTokenOf(login(email, PASSWORD, ip));
        String header = new String(java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[0]));
        assertThat(header).contains("test-kid");
    }

    // ---------- B-28 ----------

    @Test
    void b28_allErrorsUseUnifiedFormat() {
        HttpTestClient.SimpleResponse validation = register("bad-email", "weak", uniqueIp());
        assertThat(errorCode(validation)).isEqualTo("VALIDATION_ERROR");
        assertThat(validation.body()).contains("\"error\"").contains("\"message\"").contains("\"details\"");

        HttpTestClient.SimpleResponse unauthorized =
                login("unknown-" + UUID.randomUUID() + "@example.com", PASSWORD, uniqueIp());
        assertThat(errorCode(unauthorized)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unauthorized.body()).contains("\"error\"").contains("\"message\"");

        HttpTestClient.SimpleResponse me = get("/api/v1/auth/me", bearer("invalid"));
        assertThat(me.body()).contains("\"error\"").contains("\"code\"").contains("\"message\"");
    }
}
