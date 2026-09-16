package ru.shatrev.tasks.branch;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    public BranchPage list(HttpServletRequest request, @RequestParam(required = false) String q,
                           @RequestParam(required = false) String limit,
                           @RequestParam(required = false) String cursor) {
        return service.list(userId(request), q, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(HttpServletRequest request, @Valid @RequestBody BranchInput input) {
        BranchResponse created = service.create(userId(request), input);
        return ResponseEntity.created(URI.create("/api/v1/branches/" + created.id())).body(created);
    }

    @GetMapping("/{branchId}")
    public BranchResponse get(HttpServletRequest request, @PathVariable String branchId) {
        return service.get(userId(request), id(branchId));
    }

    @PutMapping("/{branchId}")
    public BranchResponse rename(HttpServletRequest request, @PathVariable String branchId,
                                 @Valid @RequestBody BranchInput input) {
        return service.rename(userId(request), id(branchId), input);
    }

    @PostMapping("/{branchId}/archive")
    public ResponseEntity<Void> archive(HttpServletRequest request, @PathVariable String branchId) {
        service.archive(userId(request), id(branchId));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(HttpServletRequest request) {
        return (UUID) request.getAttribute(InternalContextFilter.USER_ID_ATTRIBUTE);
    }

    private static UUID id(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            if (id.toString().equals(raw)) return id;
        } catch (IllegalArgumentException ignored) {
            // The same validation error applies to all malformed UUIDs.
        }
        throw ApiFailure.validation("branchId", "Некорректный идентификатор ветки");
    }
}
