package com.releasepilot.repository;

import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    List<AppUser> findAllByOrderByUsernameAsc();

    /** Locks the matching rows until the surrounding transaction ends. Must be called inside one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findByRoleAndEnabled(Role role, boolean enabled);
}