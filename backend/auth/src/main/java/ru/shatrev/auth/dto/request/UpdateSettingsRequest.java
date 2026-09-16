package ru.shatrev.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Полное обновление настроек (раздел 4.3 системного описания F-3).
 * Диапазоны дублируют CHECK-ограничения миграций V2/V3.
 */
public record UpdateSettingsRequest(
        @NotBlank(message = "Укажите единицу оценки")
        @Pattern(regexp = "hours|pomodoros", message = "Единица оценки: hours или pomodoros")
        String estimationUnit,

        @NotNull(message = "Укажите длительность помидора")
        @Min(value = 5, message = "Длительность помидора: от 5 минут")
        @Max(value = 120, message = "Длительность помидора: не более 120 минут")
        Integer pomodoroMinutes,

        @NotNull(message = "Укажите состояние игрового режима")
        Boolean gameModeEnabled,

        @NotNull(message = "Укажите стоимость часа для бюджета")
        @Min(value = 1, message = "Стоимость часа для бюджета: не менее 1")
        @Max(value = 1000, message = "Стоимость часа для бюджета: не более 1000")
        Integer budgetHourCost
) {
}
