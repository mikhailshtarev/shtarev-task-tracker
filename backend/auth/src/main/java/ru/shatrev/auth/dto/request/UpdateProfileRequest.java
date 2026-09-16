package ru.shatrev.auth.dto.request;

/**
 * Обновление профиля: name = null или пустая строка — очистить имя
 * (раздел 4.1 системного описания F-3).
 */
public record UpdateProfileRequest(
        String name
) {
}
