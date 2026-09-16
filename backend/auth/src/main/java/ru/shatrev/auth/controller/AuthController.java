package ru.shatrev.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.shatrev.auth.dto.request.ChangePasswordRequest;
import ru.shatrev.auth.dto.request.ForgotPasswordRequest;
import ru.shatrev.auth.dto.request.GoogleRequest;
import ru.shatrev.auth.dto.request.LoginRequest;
import ru.shatrev.auth.dto.request.RegisterRequest;
import ru.shatrev.auth.dto.request.ResendConfirmationRequest;
import ru.shatrev.auth.dto.request.ResetPasswordRequest;
import ru.shatrev.auth.dto.request.UpdateProfileRequest;
import ru.shatrev.auth.dto.request.UpdateSettingsRequest;
import ru.shatrev.auth.dto.response.AuthResponse;
import ru.shatrev.auth.dto.response.MeResponse;
import ru.shatrev.auth.dto.response.MessageResponse;
import ru.shatrev.auth.dto.response.UserSettingsResponse;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.security.JwtAuthenticationFilter;
import ru.shatrev.auth.service.AuthService;
import ru.shatrev.auth.service.CookieService;
import ru.shatrev.auth.service.UserSettingsService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final UserSettingsService userSettingsService;

    public AuthController(AuthService authService, CookieService cookieService,
                          UserSettingsService userSettingsService) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.userSettingsService = userSettingsService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest httpRequest) {
        authService.register(request.email(), request.password(), clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Проверьте email для подтверждения"));
    }

    @GetMapping("/confirm")
    public ResponseEntity<MessageResponse> confirm(@RequestParam("token") String token) {
        authService.confirmEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email подтверждён"));
    }

    @PostMapping("/resend-confirmation")
    public ResponseEntity<MessageResponse> resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request,
                                                              HttpServletRequest httpRequest) {
        authService.resendConfirmation(request.email(), clientIp(httpRequest));
        return ResponseEntity.ok(
                new MessageResponse("Если email зарегистрирован и не подтверждён, вы получите письмо"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        AuthService.TokenPair tokens = authService.login(request.email(), request.password(),
                clientIp(httpRequest));
        httpResponse.addHeader("Set-Cookie", cookieService.buildRefreshTokenCookie(tokens.refreshToken()).toString());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        User user = authService.getUserById(JwtAuthenticationFilter.currentUserId());
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<MeResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        User user = authService.updateProfile(JwtAuthenticationFilter.currentUserId(), request.name());
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getName()));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> settings() {
        return ResponseEntity.ok(userSettingsService.get(JwtAuthenticationFilter.currentUserId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(userSettingsService.update(JwtAuthenticationFilter.currentUserId(), request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(CookieService.REFRESH_TOKEN_COOKIE) String refreshToken,
                                                HttpServletResponse httpResponse) {
        AuthService.TokenPair tokens = authService.refresh(refreshToken);
        httpResponse.addHeader("Set-Cookie", cookieService.buildRefreshTokenCookie(tokens.refreshToken()).toString());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(CookieService.REFRESH_TOKEN_COOKIE) String refreshToken,
                                       HttpServletResponse httpResponse) {
        authService.logout(refreshToken);
        httpResponse.addHeader("Set-Cookie", cookieService.buildClearRefreshTokenCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(JwtAuthenticationFilter.currentUserId(),
                request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Пароль успешно изменён"));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        AuthService.TokenPair tokens = authService.loginWithGoogle(request.code(), clientIp(httpRequest));
        httpResponse.addHeader("Set-Cookie", cookieService.buildRefreshTokenCookie(tokens.refreshToken()).toString());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                          HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), clientIp(httpRequest));
        return ResponseEntity.ok(
                new MessageResponse("Если email зарегистрирован, вы получите ссылку для сброса пароля"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(new MessageResponse("Пароль успешно изменён"));
    }

    /** IP клиента: учитываем X-Forwarded-For от Gateway. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
