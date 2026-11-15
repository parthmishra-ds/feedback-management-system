package edu.sau.feedback.controller;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.security.AppUserDetails;
import edu.sau.feedback.service.ApiException;
import edu.sau.feedback.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;

    public AuthController(AuthService auth, AuthenticationManager authManager, SecurityContextRepository contextRepo) {
        this.auth = auth;
        this.authManager = authManager;
        this.contextRepo = contextRepo;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserInfo register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public UserInfo login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication result;
        try {
            result = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.institutionalId().trim(), req.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid college ID or password");  // same message for both cases
        }
        if (request.getSession(false) != null) request.changeSessionId();                      // prevent session fixation
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(result);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, request, response);                                        // stores login in the HTTP session
        AppUserDetails me = (AppUserDetails) result.getPrincipal();
        return new UserInfo(me.getUserId(), me.getUsername(), me.getFullName(), me.getRole(), null);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal AppUserDetails me) {
        return new UserInfo(me.getUserId(), me.getUsername(), me.getFullName(), me.getRole(), null);
    }
}
