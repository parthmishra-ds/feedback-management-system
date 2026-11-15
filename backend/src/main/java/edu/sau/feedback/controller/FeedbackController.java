package edu.sau.feedback.controller;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.security.AppUserDetails;
import edu.sau.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints for every logged-in member of the college. */
@RestController
@RequestMapping("/api")
public class FeedbackController {
    private final FeedbackService service;

    public FeedbackController(FeedbackService service) { this.service = service; }

    @GetMapping("/categories")
    public List<CategoryInfo> categories() { return service.listCategories(); }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDetail submit(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody FeedbackRequest req) {
        return service.submit(me.getUserId(), req);
    }

    @GetMapping("/feedback/mine")
    public List<FeedbackSummary> mine(@AuthenticationPrincipal AppUserDetails me) {
        return service.listMine(me.getUserId());
    }

    @GetMapping("/feedback/{id}")
    public FeedbackDetail detail(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return service.myDetail(me.getUserId(), id);
    }
}
