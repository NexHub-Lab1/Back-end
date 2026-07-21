package com.nexhub.backend.service.github;

import java.util.List;
import java.util.Optional;

public interface GithubIssueClient {
    Optional<GithubIssue> findByTaskMarker(String repositoryUrl, String accessToken, String taskMarker);

    GithubIssue createIssue(String repositoryUrl, String accessToken, String title, String body);

    GithubIssue updateIssueBody(String repositoryUrl, String accessToken, int issueNumber, String body);

    List<String> addAssignees(String repositoryUrl, String accessToken, int issueNumber, List<String> usernames);

    record GithubIssue(Long id, Integer number, String url, String state) {
    }
}
