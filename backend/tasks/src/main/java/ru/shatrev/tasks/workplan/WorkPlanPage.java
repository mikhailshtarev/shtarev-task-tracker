package ru.shatrev.tasks.workplan;

import java.util.List;

public record WorkPlanPage(List<WorkPlanResponse> items, String nextCursor) {
}
