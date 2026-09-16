package ru.shatrev.auth.service;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.auth.entity.EmailConfirmationToken;
import ru.shatrev.auth.entity.PasswordHistory;
import ru.shatrev.auth.entity.PasswordResetToken;
import ru.shatrev.auth.entity.RefreshTokenBlacklist;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.exception.ApiException;
import ru.shatrev.auth.repository.EmailConfirmationTokenRepository;
import ru.shatrev.auth.repository.PasswordHistoryRepository;
import ru.shatrev.auth.repository.PasswordResetTokenRepository;
import ru.shatrev.auth.repository.RefreshTokenBlacklistRepository;
import ru.shatrev.auth.repository.UserRepository;
import ru.shatrev.auth.security.JwtAuthenticationFilter;
import ru.shatrev.auth.security.LoginAttemptService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Бизнес-логика эндпоинтов auth (разделы 4.2–4.12 спецификации).
 * Все времена — UTC; таблицы БД хранят TIMESTAMP без зоны.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int PASSWORD_HISTORY_LIMIT = 5;
    private static final int EMAIL_TOKEN_TTL_HOURS = 24;
    private static final int RESET_TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final RefreshTokenBlacklistRepository refreshTokenBlacklistRepository;
    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final TokenGenerator tokenGenerator;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final GoogleOAuth2Service googleOAuth2Service;
    private final RefreshTokenBlacklistService refreshTokenBlacklistService;
    private final UserSettingsService userSettingsService;

    public AuthService(UserRepository userRepository,
                       PasswordHistoryRepository passwordHistoryRepository,
                       RefreshTokenBlacklistRepository refreshTokenBlacklistRepository,
                       EmailConfirmationTokenRepository emailConfirmationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordHasher passwordHasher,
                       TokenHasher tokenHasher,
                       TokenGenerator tokenGenerator,
                       JwtService jwtService,
                       EmailService emailService,
                       LoginAttemptService loginAttemptService,
                       GoogleOAuth2Service googleOAuth2Service,
                       RefreshTokenBlacklistService refreshTokenBlacklistService,
                       UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.refreshTokenBlacklistRepository = refreshTokenBlacklistRepository;
        this.emailConfirmationTokenRepository = emailConfirmationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordHasher = passwordHasher;
        this.tokenHasher = tokenHasher;
        this.tokenGenerator = tokenGenerator;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
        this.googleOAuth2Service = googleOAuth2Service;
        this.refreshTokenBlacklistService = refreshTokenBlacklistService;
        this.userSettingsService = userSettingsService;
    }

    // ---------- Регистрация и подтверждение email ----------

    @Transactional
    public void register(String email, String password, String ip) {
        requireNotRateLimited(ip, LoginAttemptService.ENDPOINT_REGISTER);
        String normalizedEmail = email.toLowerCase();

        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", 409, "Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordHasher.hash(password));
        user.setConfirmed(false);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", 409, "Пользователь с таким email уже существует");
        }

        PasswordHistory history = new PasswordHistory();
        history.setUser(user);
        history.setPasswordHash(user.getPassword());
        passwordHistoryRepository.save(history);

        userSettingsService.createDefaults(user);

        String token = tokenGenerator.generate();
        createEmailConfirmationToken(user, token);
        emailService.sendConfirmationEmail(user.getEmail(), token);
    }

    @Transactional
    public void confirmEmail(String token) {
        EmailConfirmationToken stored = emailConfirmationTokenRepository
                .findByTokenHash(tokenHasher.hash(token))
                .orElseThrow(() -> invalidConfirmationToken());
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalidConfirmationToken();
        }
        User user = stored.getUser();
        user.setConfirmed(true);
        userRepository.save(user);
        emailConfirmationTokenRepository.delete(stored);
    }

    /** Всегда 200 — статус регистрации не раскрывается (раздел 4.4). */
    @Transactional
    public void resendConfirmation(String email, String ip) {
        requireNotRateLimited(ip, LoginAttemptService.ENDPOINT_RESEND_CONFIRMATION);
        userRepository.findByEmailIgnoreCase(email.toLowerCase())
                .filter(user -> !user.isConfirmed())
                .ifPresent(user -> {
                    emailConfirmationTokenRepository.deleteByUserId(user.getId());
                    String token = tokenGenerator.generate();
                    createEmailConfirmationToken(user, token);
                    emailService.sendConfirmationEmail(user.getEmail(), token);
                });
    }

    // ---------- Вход и выход ----------

    @Transactional
    public TokenPair login(String email, String password, String ip) {
        if (loginAttemptService.isBlockedByIp(ip)) {
            throw new ApiException("RATE_LIMITED", 429, "Слишком много попыток. Попробуйте через 15 минут");
        }

        User user = userRepository.findByEmailIgnoreCase(email.toLowerCase()).orElse(null);
        if (user == null) {
            loginAttemptService.recordAttempt(null, ip, LoginAttemptService.ENDPOINT_LOGIN);
            throw invalidCredentials();
        }

        if (loginAttemptService.isBlockedByUser(user.getId())) {
            throw new ApiException("RATE_LIMITED", 429, "Слишком много попыток. Попробуйте через 15 минут");
        }

        if (!user.isConfirmed()) {
            throw new ApiException("EMAIL_NOT_CONFIRMED", 401, "Подтвердите email для входа");
        }

        if (!passwordHasher.matches(password, user.getPassword())) {
            loginAttemptService.recordAttempt(user, ip, LoginAttemptService.ENDPOINT_LOGIN);
            throw invalidCredentials();
        }

        user.setLastLoginAt(LocalDateTime.now());
        loginAttemptService.clearAttempts(user.getId());
        return issueTokens(user);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    /** Вход/регистрация через Google OAuth 2.0 (раздел 4.10). */
    @Transactional
    public TokenPair loginWithGoogle(String code, String ip) {
        GoogleOAuth2Service.GoogleProfile profile = googleOAuth2Service.exchangeCodeForProfile(code);
        User user = userRepository.findByEmailIgnoreCase(profile.email()).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(profile.email());
            user.setName(profile.name());
            user.setConfirmed(true);
            user = userRepository.saveAndFlush(user);
            userSettingsService.createDefaults(user);
        } else if (user.getPassword() != null) {
            throw new ApiException("EMAIL_CONFLICT", 409, "Email уже зарегистрирован. Войдите через email");
        }

        user.setLastLoginAt(LocalDateTime.now());
        if (user.getName() == null && profile.name() != null) {
            user.setName(profile.name());
        }
        return issueTokens(user);
    }

    // ---------- Refresh и logout ----------

    @Transactional
    public TokenPair refresh(String refreshToken) {
        Claims claims = jwtService.validateToken(refreshToken);
        if (claims == null || !JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw invalidRefreshToken();
        }

        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(AuthService::invalidRefreshToken);

        Instant iat = claims.getIssuedAt().toInstant();
        if (user.getTokensValidFrom() != null
                && iat.isBefore(user.getTokensValidFrom().toInstant(ZoneOffset.UTC))) {
            throw invalidRefreshToken();
        }

        String jti = claims.getId();
        if (refreshTokenBlacklistRepository.findByJti(jti).isPresent()) {
            throw invalidRefreshToken();
        }

        // INSERT-first: UNIQUE на jti закрывает гонку параллельных refresh (раздел 3.4).
        RefreshTokenBlacklist entry = new RefreshTokenBlacklist();
        entry.setJti(jti);
        entry.setUser(user);
        entry.setExpiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
        try {
            refreshTokenBlacklistRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw invalidRefreshToken();
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        Claims claims = jwtService.validateToken(refreshToken);
        if (claims == null || !JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw invalidRefreshToken();
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(AuthService::invalidRefreshToken);

        // Logout идемпотентен: повторный вызов с уже использованным токеном просто очищает cookie
        refreshTokenBlacklistService.addToBlacklistIfAbsent(claims.getId(), user,
                LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
    }

    // ---------- Данные пользователя ----------

    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        return userRepository.findById(id).orElseThrow(ApiException::unauthorized);
    }

    /**
     * Обновление профиля (F-3, раздел 4.1 системного описания):
     * пустое имя или null — очистить, иначе — сохранить с trim.
     */
    @Transactional
    public User updateProfile(UUID userId, String name) {
        User user = getUserById(userId);
        String normalized = name == null ? null : name.trim();
        if (normalized != null && normalized.length() > 100) {
            throw ApiException.validation("Проверьте правильность заполнения полей",
                    List.of(Map.of("field", "name", "message", "Имя должно содержать не более 100 символов")));
        }
        user.setName(normalized == null || normalized.isEmpty() ? null : normalized);
        User saved = userRepository.save(user);
        log.info("Профиль пользователя {} обновлён", userId);
        return saved;
    }

    // ---------- Смена и сброс пароля ----------

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);

        if (!passwordHasher.matches(currentPassword, user.getPassword())) {
            throw new ApiException("INVALID_CURRENT_PASSWORD", 400, "Неверный текущий пароль");
        }
        requirePasswordNotInHistory(user, newPassword);

        updatePassword(user, newPassword);
    }

    @Transactional
    public void forgotPassword(String email, String ip) {
        requireNotRateLimited(ip, LoginAttemptService.ENDPOINT_FORGOT_PASSWORD);
        userRepository.findByEmailIgnoreCase(email.toLowerCase())
                .ifPresent(user -> {
                    passwordResetTokenRepository.deleteByUserId(user.getId());
                    String token = tokenGenerator.generate();
                    PasswordResetToken stored = new PasswordResetToken();
                    stored.setUser(user);
                    stored.setTokenHash(tokenHasher.hash(token));
                    stored.setExpiresAt(LocalDateTime.now().plusHours(RESET_TOKEN_TTL_HOURS));
                    passwordResetTokenRepository.save(stored);
                    emailService.sendResetPasswordEmail(user.getEmail(), token);
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHash(tokenHasher.hash(token))
                .orElseThrow(AuthService::invalidResetToken);
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalidResetToken();
        }

        User user = stored.getUser();
        requirePasswordNotInHistory(user, newPassword);
        updatePassword(user, newPassword);
        passwordResetTokenRepository.deleteByUserId(user.getId());
    }

    // ---------- Внутренние методы ----------

    private TokenPair issueTokens(User user) {
        return new TokenPair(jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user));
    }

    private void createEmailConfirmationToken(User user, String token) {
        EmailConfirmationToken stored = new EmailConfirmationToken();
        stored.setUser(user);
        stored.setTokenHash(tokenHasher.hash(token));
        stored.setExpiresAt(LocalDateTime.now().plusHours(EMAIL_TOKEN_TTL_HOURS));
        emailConfirmationTokenRepository.save(stored);
    }

    private void requireNotRateLimited(String ip, String endpoint) {
        if (loginAttemptService.isRateLimitedByIp(ip, endpoint)) {
            throw new ApiException("RATE_LIMITED", 429, "Слишком много запросов. Попробуйте позже");
        }
        loginAttemptService.recordAttempt(null, ip, endpoint);
    }

    /** Новый пароль не должен совпадать с последними 5 из password_history (раздел 4.9/4.12). */
    private void requirePasswordNotInHistory(User user, String newPassword) {
        List<PasswordHistory> recent = passwordHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, PASSWORD_HISTORY_LIMIT));
        boolean matchesRecent = recent.stream()
                .anyMatch(entry -> passwordHasher.matches(newPassword, entry.getPasswordHash()));
        if (matchesRecent) {
            throw new ApiException("PASSWORD_TOO_RECENT", 409, "Нельзя использовать последний пароль");
        }
    }

    /** Смена пароля: обновление users.password, история (последние 5) и отзыв всех refresh-токенов. */
    private void updatePassword(User user, String newPassword) {
        String newHash = passwordHasher.hash(newPassword);
        user.setPassword(newHash);
        user.setTokensValidFrom(LocalDateTime.now());
        userRepository.save(user);

        passwordHistoryRepository.save(historyEntry(user, newHash));
        List<PasswordHistory> all = passwordHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, PASSWORD_HISTORY_LIMIT));
        List<UUID> keepIds = all.stream().map(PasswordHistory::getId).toList();
        passwordHistoryRepository.deleteByUserIdAndIdNotIn(user.getId(), keepIds);
    }

    private PasswordHistory historyEntry(User user, String hash) {
        PasswordHistory entry = new PasswordHistory();
        entry.setUser(user);
        entry.setPasswordHash(hash);
        return entry;
    }

    private static ApiException invalidCredentials() {
        return new ApiException("INVALID_CREDENTIALS", 401, "Неверный email или пароль");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException("INVALID_REFRESH_TOKEN", 401, "Токен обновления недействителен");
    }

    private static ApiException invalidConfirmationToken() {
        return new ApiException("INVALID_TOKEN", 400, "Неверный или истёкший токен подтверждения");
    }

    private static ApiException invalidResetToken() {
        return new ApiException("INVALID_TOKEN", 400, "Неверный или истёкший токен");
    }
}
