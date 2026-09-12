package ru.shatrev.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import ru.shatrev.auth.validation.ValidPassword;

public record ChangePasswordRequest(
        @NotBlank(message = "Укажите текущий пароль")
        String currentPassword,

        @ValidPassword
        String newPassword
) {
}
