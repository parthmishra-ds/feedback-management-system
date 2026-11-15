package edu.sau.feedback.controller;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.entity.Status;
import edu.sau.feedback.security.AppUserDetails;
import edu.sau.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Management only. SecurityConfig restricts /api/admin/** to ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final FeedbackService service;

    public AdminController(FeedbackService service) { this.service = service; }

    @GetMapping("/dashboard")
    public Dashboard dashboard() { return service.dashboard(); }

    @GetMapping("/feedback")
    public PageDto<FeedbackSummary> list(@RequestParam(required = false) Status status,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(defaultValue = "false") boolean pending,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return service.adminList(status, categoryId, pending, page, size);
    }

    @GetMapping("/feedback/{id}")
    public FeedbackDetail detail(@PathVariable Long id) { return service.adminDetail(id); }

    @PatchMapping("/feedback/{id}/status")
    public FeedbackDetail changeStatus(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                       @Valid @RequestBody StatusChangeRequest req) {
        return service.changeStatus(me.getUserId(), id, req);
    }

    @PostMapping("/feedback/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDetail respond(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                  @Valid @RequestBody ResponseRequest req) {
        return service.addResponse(me.getUserId(), id, req);
    }
}
