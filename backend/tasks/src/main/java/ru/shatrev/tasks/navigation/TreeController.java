package ru.shatrev.tasks.navigation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.shatrev.tasks.branch.InternalContextFilter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/navigation/tree")
public class TreeController {
    private final TreeService service;

    public TreeController(TreeService service) {
        this.service = service;
    }

    @GetMapping
    public TreePage page(HttpServletRequest request, @RequestParam(required = false) String parentType,
                         @RequestParam(required = false) String parentId,
                         @RequestParam(required = false) String limit,
                         @RequestParam(required = false) String cursor) {
        UUID userId = (UUID) request.getAttribute(InternalContextFilter.USER_ID_ATTRIBUTE);
        return service.page(userId, parentType, parentId, limit, cursor);
    }
}
