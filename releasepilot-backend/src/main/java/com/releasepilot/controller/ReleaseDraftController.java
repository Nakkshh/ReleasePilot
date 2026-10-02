package com.releasepilot.controller;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.dto.CreateDraftRequest;
import com.releasepilot.dto.DraftSummary;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.RejectRequest;
import com.releasepilot.service.DraftService;
import com.releasepilot.service.ReleasePublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public DraftView create(@Valid @RequestBody CreateDraftRequest request) {
        return service.create(request.tag(), Boolean.TRUE.equals(request.prerelease()));
    }

    @GetMapping
    public List<DraftSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DraftView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/reject")
    public DraftView reject(@PathVariable long id,
                            @Valid @RequestBody(required = false) RejectRequest request) {
        return service.reject(id,
                request == null ? null : request.decidedBy(),
                request == null ? null : request.comment());
    }

    @PostMapping("/{id}/approve")
    public DraftView approve(@PathVariable long id,
                             @Valid @RequestBody(required = false) ApproveRequest request) {
        ApproveRequest r = request == null ? new ApproveRequest(null, null, null, null, null) : request;
        return publisher.approve(id, r);
    }

    @PostMapping("/{id}/retry")
    public DraftView retry(@PathVariable long id) {
        return publisher.retry(id);
    }
}