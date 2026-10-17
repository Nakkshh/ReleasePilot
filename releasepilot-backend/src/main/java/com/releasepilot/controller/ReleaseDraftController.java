package com.releasepilot.controller;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.dto.CreateDraftRequest;
import com.releasepilot.dto.DraftSummary;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.PublicDraftSummary;
import com.releasepilot.dto.PublicDraftView;
import com.releasepilot.dto.RejectRequest;
import com.releasepilot.service.DraftService;
import com.releasepilot.service.ReleasePublisher;
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

/**
 * GET endpoints are public (read-only demo). Every other method requires the APPROVER role (see SecurityConfig).
 * The "decidedBy" field in request bodies is ignored: the decider is always the authenticated user.
 */
@RestController
@RequestMapping("/api/release/drafts")
public class ReleaseDraftController {

    private final DraftService service;
    private final ReleasePublisher publisher;

    public ReleaseDraftController(DraftService service, ReleasePublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView create(@Valid @RequestBody CreateDraftRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.create(request.tag(), Boolean.TRUE.equals(request.prerelease()), jwt.getSubject());
    }

    /** Anonymous visitors get the trimmed view; any signed-in user (APPROVER or ADMIN) gets the full one. */
    @GetMapping
    public List<?> list(@AuthenticationPrincipal Jwt jwt) {
        List<DraftSummary> all = service.list();
        if (jwt != null) {
            return all;
        }
        return all.stream().map(PublicDraftSummary::from).toList();
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        DraftView view = service.get(id);
        return jwt != null ? view : PublicDraftView.from(view);
    }

    @PostMapping("/{id}/reject")
    public DraftView reject(@PathVariable long id,
                            @Valid @RequestBody(required = false) RejectRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        return service.reject(id, jwt.getSubject(), request == null ? null : request.comment());
    }

    @PostMapping("/{id}/approve")
    public DraftView approve(@PathVariable long id,
                             @Valid @RequestBody(required = false) ApproveRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        ApproveRequest in = request == null ? new ApproveRequest(null, null, null, null, null) : request;
        ApproveRequest verified = new ApproveRequest(
                jwt.getSubject(), in.comment(), in.override(), in.overrideReason(), in.title());
        return publisher.approve(id, verified);
    }

    @PostMapping("/{id}/retry")
    public DraftView retry(@PathVariable long id) {
        return publisher.retry(id);
    }
}