package com.releasepilot.controller;

import com.releasepilot.auth.UserAdminService;
import com.releasepilot.dto.ChangePasswordRequest;
import com.releasepilot.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Any signed-in user may change their own password. The old token stops working; a fresh one is returned. */
@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final UserAdminService service;

    public AccountController(UserAdminService service) {
        this.service = service;
    }

    @PostMapping("/password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.changeOwnPassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
    }
}