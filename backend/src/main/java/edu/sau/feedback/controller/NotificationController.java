package edu.sau.feedback.controller;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.security.AppUserDetails;
import edu.sau.feedback.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The bell and the "where should we alert you" settings. Every call is scoped to the logged-in user. */
@RestController
@RequestMapping("/api")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping("/notifications")
    public NotificationList list(@AuthenticationPrincipal AppUserDetails me) { return service.list(me.getUserId()); }

    @PostMapping("/notifications/read-all")
    public NotificationList readAll(@AuthenticationPrincipal AppUserDetails me) { return service.markAllRead(me.getUserId()); }

    @PostMapping("/notifications/{id}/read")
    public NotificationList read(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) { return service.markRead(me.getUserId(), id); }

    @GetMapping("/me/contact")
    public ContactInfo contact(@AuthenticationPrincipal AppUserDetails me) { return service.contact(me.getUserId()); }

    @PutMapping("/me/contact")
    public ContactInfo saveContact(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ContactRequest req) {
        return service.updateContact(me.getUserId(), req);
    }
}
