package com.releasepilot.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ActionsModels {

    private ActionsModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunsResponse(
            @JsonProperty("total_count") Integer totalCount,
            @JsonProperty("workflow_runs") List<Run> runs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Run(
            Long id,
            String name,
            @JsonProperty("head_branch") String headBranch,
            @JsonProperty("head_sha") String headSha,
            String status,
            String conclusion,
            String event,
            @JsonProperty("run_number") Integer runNumber,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("created_at") String createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobsResponse(
            @JsonProperty("total_count") Integer totalCount,
            List<Job> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Job(
            Long id,
            String name,
            String status,
            String conclusion,
            List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String name,
            String status,
            String conclusion,
            Integer number) {
    }
}