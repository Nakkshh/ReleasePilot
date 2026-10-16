package com.releasepilot.auth;

import com.jayway.jsonpath.JsonPath;
import com.releasepilot.exception.AuthException;
import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;
import com.releasepilot.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "auth.verify-user-on-request=true")
class UserManagementTest {

    private static final String SECRET = "test-jwt-secret-for-unit-tests-0123456789";   // same as application-test.properties
    private static final String PW = "correct-horse-battery";
    private static final String AUTH = "Authorization";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired UserAdminService userAdmin;

    @BeforeEach
    void seed() {
        users.deleteAll();
        Instant past = Instant.now().minus(Duration.ofHours(1));
        users.save(new AppUser("root", encoder.encode(PW), Role.ADMIN, true, past));
        users.save(new AppUser("alice", encoder.encode(PW), Role.APPROVER, true, past));
    }

    @AfterEach
    void clean() {
        users.deleteAll();
    }

    // ------------------------------------------------------------ helpers

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON).content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long idOf(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    // ------------------------------------------------------------ admin API

    @Test
    void adminCreatesAndListsUsers() throws Exception {
        String admin = login("root", PW);

        mvc.perform(post("/api/admin/users").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"username\":\"  Carol \",\"password\":\"" + PW + "\",\"role\":\"APPROVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.role").value("APPROVER"))
                .andExpect(jsonPath("$.enabled").value(true));

        String list = mvc.perform(get("/api/admin/users").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> usernames = JsonPath.read(list, "$[*].username");
        assertThat(usernames).containsExactly("alice", "carol", "root");
        assertThat(list).doesNotContain("passwordHash").doesNotContain("$2a$");

        login("carol", PW);   // the new account works
    }

    @Test
    void nonAdminsAndAnonymousVisitorsCannotManageUsers() throws Exception {
        String approver = login("alice", PW);

        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header(AUTH, bearer(approver)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/admin/users").header(AUTH, bearer(approver)).contentType(JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"" + PW + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        assertThat(users.findByUsername("mallory")).isEmpty();
    }

    @Test
    void duplicateUsernamesAreRefusedCaseInsensitively() throws Exception {
        String admin = login("root", PW);

        mvc.perform(post("/api/admin/users").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"username\":\"ROOT\",\"password\":\"" + PW + "\",\"role\":\"APPROVER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void badInputIsRejectedWithClearCodes() throws Exception {
        String admin = login("root", PW);

        mvc.perform(post("/api/admin/users").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"username\":\"a b\",\"password\":\"" + PW + "\",\"role\":\"APPROVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        mvc.perform(post("/api/admin/users").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"username\":\"dave\",\"password\":\"short\",\"role\":\"APPROVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mvc.perform(post("/api/admin/users").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"username\":\"dave\",\"password\":\"" + PW + "\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(users.findByUsername("dave")).isEmpty();
    }

    // ------------------------------------------------------------ per-request account check

    @Test
    void disablingAUserKillsTheirExistingTokenAtOnce() throws Exception {
        String admin = login("root", PW);
        String aliceToken = login("alice", PW);
        mvc.perform(get("/api/auth/me").header(AUTH, bearer(aliceToken))).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + idOf("alice") + "/disable").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/auth/me").header(AUTH, bearer(aliceToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/auth/login").contentType(JSON).content(loginBody("alice", PW)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/admin/users/" + idOf("alice") + "/enable").header(AUTH, bearer(admin)))
                .andExpect(status().isOk());
        login("alice", PW);
    }

    @Test
    void aDemotionAppliesToTokensAlreadyIssued() throws Exception {
        users.save(new AppUser("boss", encoder.encode(PW), Role.ADMIN, true, Instant.now().minus(Duration.ofHours(1))));
        String admin = login("root", PW);
        String bossToken = login("boss", PW);
        mvc.perform(get("/api/admin/users").header(AUTH, bearer(bossToken))).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + idOf("boss") + "/role").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"role\":\"APPROVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APPROVER"));

        mvc.perform(get("/api/admin/users").header(AUTH, bearer(bossToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header(AUTH, bearer(bossToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("APPROVER"));
    }

    // ------------------------------------------------------------ guards

    @Test
    void anAdminCannotDisableOrDemoteThemselves() throws Exception {
        String admin = login("root", PW);

        mvc.perform(post("/api/admin/users/" + idOf("root") + "/disable").header(AUTH, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        mvc.perform(post("/api/admin/users/" + idOf("root") + "/role").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"role\":\"APPROVER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void theLastEnabledAdminCannotBeDisabledOrDemoted() {
        long rootId = idOf("root");

        assertThatThrownBy(() -> userAdmin.setEnabled(rootId, false, "someone-else"))
                .isInstanceOfSatisfying(AuthException.class, e -> assertThat(e.getCode()).isEqualTo("LAST_ADMIN"));
        assertThatThrownBy(() -> userAdmin.setRole(rootId, Role.APPROVER, "someone-else"))
                .isInstanceOfSatisfying(AuthException.class, e -> assertThat(e.getCode()).isEqualTo("LAST_ADMIN"));

        users.save(new AppUser("boss", encoder.encode(PW), Role.ADMIN, true, Instant.now()));
        assertThat(userAdmin.setEnabled(rootId, false, "boss").enabled()).isFalse();   // a second admin exists now
    }

    // ------------------------------------------------------------ passwords

    @Test
    void resettingAPasswordInvalidatesOlderTokens() throws Exception {
        String admin = login("root", PW);
        String oldToken = new JwtService(SECRET, 60, Clock.fixed(Instant.now().minus(Duration.ofMinutes(10)), ZoneOffset.UTC))
                .issue("alice", Role.APPROVER).token();
        mvc.perform(get("/api/auth/me").header(AUTH, bearer(oldToken))).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + idOf("alice") + "/password").header(AUTH, bearer(admin)).contentType(JSON)
                        .content("{\"password\":\"a-brand-new-passphrase\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header(AUTH, bearer(oldToken))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(JSON).content(loginBody("alice", PW)))
                .andExpect(status().isUnauthorized());
        login("alice", "a-brand-new-passphrase");
    }

    @Test
    void changingYourOwnPasswordNeedsTheCurrentOneAndReturnsAFreshToken() throws Exception {
        String token = login("alice", PW);

        mvc.perform(post("/api/auth/password").header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"another-long-passphrase-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        mvc.perform(post("/api/auth/password").header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"" + PW + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        String body = mvc.perform(post("/api/auth/password").header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"another-long-passphrase-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/auth/me").header(AUTH, bearer(fresh))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(JSON).content(loginBody("alice", PW)))
                .andExpect(status().isUnauthorized());
        login("alice", "another-long-passphrase-1");
    }
}Invoke-RestMethod -Method Post "$base/admin/users/$id/disable" -Headers $A