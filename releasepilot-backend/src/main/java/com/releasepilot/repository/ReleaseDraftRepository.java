package com.releasepilot.repository;

import com.releasepilot.model.ReleaseDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import com.releasepilot.model.DraftStatus;
import java.util.Collection;

import java.util.List;

public interface ReleaseDraftRepository extends JpaRepository<ReleaseDraft, Long> {

    List<ReleaseDraft> findAllByOrderByCreatedAtDesc();

    boolean existsByTagAndStatusIn(String tag, Collection<DraftStatus> statuses);

    List<ReleaseDraft> findTop50ByOrderByCreatedAtDesc();
}