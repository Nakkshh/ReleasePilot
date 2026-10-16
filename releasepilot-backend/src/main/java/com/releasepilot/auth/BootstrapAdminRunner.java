package com.releasepilot.auth;

import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;
import com.releasepilot.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** Creates the first ADMIN from environment variables, only while the users table is empty. */
@Component
@Order(30)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
    static final int MIN_PASSWORD = 12;
    static final int MAX_PASSWORD = 72;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(AppUserRepository users,
                                PasswordEncoder encoder,
                                @Value("${auth.bootstrap-admin.username:}") String username,
                                @Value("${auth.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    void bootstrap() {
        if (users.count() > 0) {
            return;
        }
        String name = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || password == null || password.isEmpty()) {
            log.warn("[AUTH] No users exist and no bootstrap admin is configured. Set RELEASEPILOT_BOOTSTRAP_USER "
                    + "and RELEASEPILOT_BOOTSTRAP_PASSWORD and restart to create the first admin.");
            return;
        }
        if (!USERNAME.matcher(name).matches()) {
            log.warn("[AUTH] Bootstrap admin not created: the username must be 3-50 characters of a-z 0-9 . _ -");
            return;
        }
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            log.warn("[AUTH] Bootstrap admin not created: the password must be {}-{} characters.",
                    MIN_PASSWORD, MAX_PASSWORD);
            return;
        }
        users.save(new AppUser(name, encoder.encode(password), Role.ADMIN, true, Instant.now()));
        log.info("[AUTH] Created the first admin user '{}'. You can remove the bootstrap variables now.", name);
    }
}