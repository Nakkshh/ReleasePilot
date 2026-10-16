package com.releasepilot.auth;

import com.releasepilot.dto.LoginResponse;
import com.releasepilot.dto.UserView;
import com.releasepilot.exception.AuthException;
import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;
import com.releasepilot.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;

    public UserAdminService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserView::of).toList();
    }

    /** Not @Transactional on purpose: a unique-constraint race surfaces from saveAndFlush and is mapped to 409. */
    public UserView create(String rawUsername, String password, Role role, String actor) {
        String username = rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new AuthException("INVALID_USERNAME",
                    "Username must be 3-50 characters of a-z 0-9 . _ -", HttpStatus.BAD_REQUEST);
        }
        PasswordPolicy.check(username, password);
        if (users.findByUsername(username).isPresent()) {
            throw taken();
        }
        try {
            AppUser saved = users.saveAndFlush(
                    new AppUser(username, encoder.encode(password), role, true, clock.instant()));
            log.info("[USERS] '{}' created user '{}' with role {}", actor, username, role);
            return UserView.of(saved);
        } catch (DataIntegrityViolationException e) {
            throw taken();
        }
    }

    @Transactional
    public UserView setEnabled(long id, boolean enabled, String actor) {
        List<AppUser> admins = users.findByRoleAndEnabled(Role.ADMIN, true);   // row locks serialise admin changes
        AppUser target = find(id);
        if (!enabled) {
            refuseSelf(target, actor);
            if (target.isEnabled() && target.getRole() == Role.ADMIN && admins.size() <= 1) {
                throw lastAdmin();
            }
        }
        target.setEnabled(enabled);
        log.info("[USERS] '{}' {} user '{}'", actor, enabled ? "enabled" : "disabled", target.getUsername());
        return UserView.of(target);
    }

    @Transactional
    public UserView setRole(long id, Role role, String actor) {
        List<AppUser> admins = users.findByRoleAndEnabled(Role.ADMIN, true);
        AppUser target = find(id);
        refuseSelf(target, actor);
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && target.isEnabled() && admins.size() <= 1) {
            throw lastAdmin();
        }
        target.setRole(role);
        log.info("[USERS] '{}' set the role of '{}' to {}", actor, target.getUsername(), role);
        return UserView.of(target);
    }

    @Transactional
    public void resetPassword(long id, String newPassword, String actor) {
        AppUser target = find(id);
        PasswordPolicy.check(target.getUsername(), newPassword);
        target.changePassword(encoder.encode(newPassword), clock.instant());
        log.info("[USERS] '{}' reset the password of '{}'", actor, target.getUsername());
    }

    /** Returns a fresh token, because the change invalidates the caller's current one. */
    @Transactional
    public LoginResponse changeOwnPassword(String username, String current, String next) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (current == null || !encoder.matches(current, user.getPasswordHash())) {
            throw new AuthException("CURRENT_PASSWORD_WRONG", "The current password is wrong.", HttpStatus.BAD_REQUEST);
        }
        if (current.equals(next)) {
            throw new AuthException("WEAK_PASSWORD", "The new password must differ from the current one.",
                    HttpStatus.BAD_REQUEST);
        }
        PasswordPolicy.check(user.getUsername(), next);
        user.changePassword(encoder.encode(next), clock.instant());
        log.info("[USERS] '{}' changed their own password", user.getUsername());
        JwtService.IssuedToken issued = jwt.issue(user.getUsername(), user.getRole());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), user.getUsername(), issued.roles());
    }

    private AppUser find(long id) {
        return users.findById(id)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "User " + id + " not found.", HttpStatus.NOT_FOUND));
    }

    private static void refuseSelf(AppUser target, String actor) {
        if (target.getUsername().equals(actor)) {
            throw new AuthException("CANNOT_MODIFY_SELF",
                    "You cannot disable or change the role of your own account.", HttpStatus.CONFLICT);
        }
    }

    private static AuthException lastAdmin() {
        return new AuthException("LAST_ADMIN",
                "This is the last enabled admin. Create or enable another admin first.", HttpStatus.CONFLICT);
    }

    private static AuthException taken() {
        return new AuthException("USERNAME_TAKEN", "That username is already taken.", HttpStatus.CONFLICT);
    }
}