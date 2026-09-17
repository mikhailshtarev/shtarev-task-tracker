package ru.shatrev.tasks.workplan;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.InternalContextFilter;

import java.net.URI;
import java.util.UUID;

@RestController
public class WorkPlanController {
    private final WorkPlanService service;

    public WorkPlanController(WorkPlanService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/branches/{branchId}/plans")
    public WorkPlanPage list(HttpServletRequest request, @PathVariable String branchId,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String limit,
                             @RequestParam(required = false) String cursor) {
        return service.list(userId(request), id(branchId, "branchId"), q, limit, cursor);
    }

    @PostMapping("/api/v1/branches/{branchId}/plans")
    public ResponseEntity<WorkPlanResponse> create(HttpServletRequest request, @PathVariable String branchId,
                                                    @RequestBody WorkPlanInput input) {
        WorkPlanResponse created = service.create(userId(request), id(branchId, "branchId"), input);
        return ResponseEntity.created(URI.create("/api/v1/work-plans/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/work-plans/{planId}")
    public WorkPlanResponse get(HttpServletRequest request, @PathVariable String planId) {
        return service.get(userId(request), id(planId, "planId"));
    }

    @PutMapping("/api/v1/work-plans/{planId}")
    public WorkPlanResponse rename(HttpServletRequest request, @PathVariable String planId,
                                   @RequestBody WorkPlanInput input) {
        return service.rename(userId(request), id(planId, "planId"), input);
    }

    @PostMapping("/api/v1/work-plans/{planId}/archive")
    public ResponseEntity<Void> archive(HttpServletRequest request, @PathVariable String planId) {
        service.archive(userId(request), id(planId, "planId"));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(HttpServletRequest request) {
        return (UUID) request.getAttribute(InternalContextFilter.USER_ID_ATTRIBUTE);
    }

    private static UUID id(String raw, String field) {
        try {
            UUID id = UUID.fromString(raw);
            if (id.toString().equals(raw)) return id;
        } catch (IllegalArgumentException ignored) {
            // All malformed UUIDs have the same public validation error.
        }
        throw ApiFailure.validation(field, "Некорректный идентификатор");
    }
}
