package ru.shatrev.auth.dto.response;

import ru.shatrev.auth.entity.EstimationUnit;

public record UserSettingsResponse(
        EstimationUnit estimationUnit,
        int pomodoroMinutes,
        boolean gameModeEnabled,
        int budgetHourCost
) {
}
