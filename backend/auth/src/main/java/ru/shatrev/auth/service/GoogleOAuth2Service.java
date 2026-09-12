package ru.shatrev.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.shatrev.auth.exception.ApiException;

/**
 * Обмен authorization code на ID token через Google Token Endpoint (раздел 2.2, 4.10).
 * Spring Security OAuth 2.0 Client не используется — SPA-сценарий.
 */
@Service
public class GoogleOAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2Service.class);

    private final GoogleProperties properties;
    private final RestClient restClient;
    private final JwtDecoder idTokenDecoder;

    public GoogleOAuth2Service(GoogleProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        // NimbusJwtDecoder кэширует JWKS и повторно загружает при неизвестном kid.
        this.idTokenDecoder = decoder;
    }

    /** Валидирует ID token (signature, issuer, audience) и извлекает профиль. */
    public GoogleProfile exchangeCodeForProfile(String code) {
        String idToken = requestToken(code);
        try {
            Jwt jwt = idTokenDecoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
            }
            if (!properties.issuer().equals(jwt.getIssuer() != null ? jwt.getIssuer().toString() : null)) {
                throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(properties.clientId())) {
                throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
            }
            return new GoogleProfile(email.toLowerCase(), jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
        } catch (JwtValidationException e) {
            log.info("Google ID token validation failed: {}", e.getMessage());
            throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
        }
    }

    private String requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        try {
            TokenResponse response = restClient.post()
                    .uri(properties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.idToken() == null || response.idToken().isBlank()) {
                throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
            }
            return response.idToken();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.info("Google token endpoint call failed: {}", e.getMessage());
            throw new ApiException("INVALID_GOOGLE_CODE", 400, "Неверный код авторизации Google");
        }
    }

    public record GoogleProfile(String email, String name, String picture) {
    }

    public record TokenResponse(String accessToken, String idToken) {
    }

    @ConfigurationProperties(prefix = "auth.google")
    public record GoogleProperties(
            String clientId,
            String clientSecret,
            String redirectUri,
            String tokenEndpoint,
            String jwksUri,
            String issuer
    ) {
    }
}
