package ru.shatrev.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.auth.dto.request.UpdateSettingsRequest;
import ru.shatrev.auth.dto.response.UserSettingsResponse;
import ru.shatrev.auth.entity.EstimationUnit;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.entity.UserSettings;
import ru.shatrev.auth.exception.ApiException;
import ru.shatrev.auth.repository.UserRepository;
import ru.shatrev.auth.repository.UserSettingsRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Персональные настройки пользователя (F-3). Строка настроек создаётся при
 * регистрации; при отсутствии — fallback с дефолтами. PUT инвалидирует
 * Redis-ключ settings:{userId}, который читают другие сервисы (Tasks, Game).
 */
@Service
public class UserSettingsService {

    public static final String CACHE_KEY_PREFIX = "settings:";

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserSettingsService(UserSettingsRepository userSettingsRepository,
                               UserRepository userRepository,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** Дефолтные настройки нового пользователя (значения — раздел 6 бизнес-описания). */
    @Transactional
    public void createDefaults(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        userSettingsRepository.save(settings);
    }

    @Transactional
    public UserSettingsResponse get(UUID userId) {
        UserSettingsResponse cached = readCache(userId);
        if (cached != null) {
            return cached;
        }

        UserSettingsResponse response = toResponse(getOrCreate(userId));
        writeCache(userId, response);
        return response;
    }

    @Transactional
    public UserSettingsResponse update(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings = getOrCreate(userId);
        settings.setEstimationUnit(EstimationUnit.fromValue(request.estimationUnit()));
        settings.setPomodoroMinutes(request.pomodoroMinutes());
        settings.setGameModeEnabled(request.gameModeEnabled());
        settings.setBudgetHourCost(request.budgetHourCost());
        if (request.pomodoroMinutes() % 5 != 0) {
            throw ApiException.validation("Проверьте правильность заполнения полей",
                    List.of(Map.of("field", "pomodoroMinutes",
                            "message", "Длительность помидора должна быть кратна 5 минутам")));
        }
        UserSettings saved = userSettingsRepository.saveAndFlush(settings);

        invalidateCache(userId);
        log.info("Настройки пользователя {} обновлены", userId);
        return toResponse(saved);
    }

    private UserSettings getOrCreate(UUID userId) {
        return userSettingsRepository.findByUserId(userId).orElseGet(() -> {
            UserSettings defaults = new UserSettings();
            defaults.setUser(userRepository.getReferenceById(userId));
            return userSettingsRepository.saveAndFlush(defaults);
        });
    }

    private UserSettingsResponse readCache(UUID userId) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey(userId));
            return value == null ? null : objectMapper.readValue(value, UserSettingsResponse.class);
        } catch (Exception e) {
            log.debug("Кэш настроек недоступен для пользователя {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void writeCache(UUID userId, UserSettingsResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey(userId), objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать кэш настроек пользователя {}: {}", userId, e.getMessage());
        } catch (Exception e) {
            log.debug("Кэш настроек недоступен для пользователя {}: {}", userId, e.getMessage());
        }
    }

    /** Сбой Redis не ломает PUT настроек — кэш протухнет по TTL 15 минут. */
    private void invalidateCache(UUID userId) {
        try {
            redisTemplate.delete(cacheKey(userId));
        } catch (Exception e) {
            log.warn("Не удалось инвалидировать кэш настроек пользователя {}: {}", userId, e.getMessage());
        }
    }

    private static UserSettingsResponse toResponse(UserSettings settings) {
        return new UserSettingsResponse(settings.getEstimationUnit(), settings.getPomodoroMinutes(),
                settings.isGameModeEnabled(), settings.getBudgetHourCost());
    }

    private static String cacheKey(UUID userId) {
        return CACHE_KEY_PREFIX + userId;
    }
}
