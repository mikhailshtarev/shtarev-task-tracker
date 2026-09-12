package ru.shatrev.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.auth.entity.RefreshTokenBlacklist;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.exception.ApiException;
import ru.shatrev.auth.repository.RefreshTokenBlacklistRepository;

import java.time.LocalDateTime;

/**
 * Single-use refresh-токены: jti использованного токена попадает в blacklist,
 * новые токены выдаются только после успешного INSERT (атомарность через UNIQUE на jti).
 */
@Service
public class RefreshTokenBlacklistService {

    private final RefreshTokenBlacklistRepository repository;

    public RefreshTokenBlacklistService(RefreshTokenBlacklistRepository repository) {
        this.repository = repository;
    }

    /**
     * INSERT-first: при гонке двух параллельных refresh с одним токеном второй INSERT
     * нарушает UNIQUE на jti и получает 401 (раздел 3.4).
     */
    public void addToBlacklist(String jti, User user, LocalDateTime expiresAt) {
        RefreshTokenBlacklist entry = new RefreshTokenBlacklist();
        entry.setJti(jti);
        entry.setUser(user);
        entry.setExpiresAt(expiresAt);
        try {
            repository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("INVALID_REFRESH_TOKEN", 401, "Токен обновления недействителен");
        }
    }

    /**
     * Идемпотентная вставка для logout: повторный logout не должен ломать ответ.
     * REQUIRES_NEW — конфликт UNIQUE не помечает внешнюю транзакцию rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
    public void addToBlacklistIfAbsent(String jti, User user, LocalDateTime expiresAt) {
        if (repository.findByJti(jti).isPresent()) {
            return;
        }
        RefreshTokenBlacklist entry = new RefreshTokenBlacklist();
        entry.setJti(jti);
        entry.setUser(user);
        entry.setExpiresAt(expiresAt);
        try {
            repository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            // Параллельный logout уже вставил jti — это норма
        }
    }

    public boolean isBlacklisted(String jti) {
        return repository.findByJti(jti).isPresent();
    }

    @Transactional
    @Scheduled(cron = "0 30 */6 * * *")
    public void cleanup() {
        repository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
