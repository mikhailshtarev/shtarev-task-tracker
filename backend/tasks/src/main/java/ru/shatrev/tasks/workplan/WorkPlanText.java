package ru.shatrev.tasks.workplan;

import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.BranchText;

public final class WorkPlanText {
    private WorkPlanText() {
    }

    public static String name(String raw) {
        if (raw == null) throw ApiFailure.validation("name", "Укажите название плана");
        String value = BranchText.normalize(raw);
        int length = value.codePointCount(0, value.length());
        if (length < 2 || length > 150) {
            throw ApiFailure.validation("name", "Название должно содержать от 2 до 150 символов");
        }
        return value;
    }

    public static String query(String raw) {
        String value = raw == null ? "" : BranchText.normalize(raw);
        if (value.codePointCount(0, value.length()) > 150) {
            throw ApiFailure.validation("q", "Поисковый запрос должен содержать не более 150 символов");
        }
        return value;
    }
}
