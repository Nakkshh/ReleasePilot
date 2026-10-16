package com.releasepilot.controller;

import com.releasepilot.auth.UserAdminService;
import com.releasepilot.dto.CreateUserRequest;
import com.releasepilot.dto.ResetPasswordRequest;
import com.releasepilot.dto.SetRoleRequest;
import com.releasepilot.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.create(request.username(), request.password(), request.role(), jwt.getSubject());
    }

    @PostMapping("/{id}/enable")
    public UserView enable(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return service.setEnabled(id, true, jwt.getSubject());
    }

    @PostMapping("/{id}/disable")
    public UserView disable(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return service.setEnabled(id, false, jwt.getSubject());
    }

    @PostMapping("/{id}/role")
    public UserView setRole(@PathVariable long id, @Valid @RequestBody SetRoleRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        return service.setRole(id, request.role(), jwt.getSubject());
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        service.resetPassword(id, request.password(), jwt.getSubject());
    }
}