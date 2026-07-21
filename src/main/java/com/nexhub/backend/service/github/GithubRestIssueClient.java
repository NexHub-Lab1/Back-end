package com.nexhub.backend.service.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GithubRestIssueClient implements GithubIssueClient {
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String USER_AGENT = "NexHub";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public Optional<GithubIssue> findByTaskMarker(String repositoryUrl, String accessToken, String taskMarker) {
        RepoSlug repo = parseRepo(repositoryUrl);
        HttpRequest request = requestBuilder(repo.issuesUri("?state=all&per_page=100"), accessToken).GET().build();
        JsonNode issues = readSuccessful(send(request), "Unable to inspect GitHub issues");
        for (JsonNode issue : issues) {
            if (!issue.has("pull_request") && issue.path("body").asText("").contains(taskMarker)) {
                return Optional.of(toIssue(issue));
            }
        }
        return Optional.empty();
    }

    @Override
    public GithubIssue createIssue(String repositoryUrl, String accessToken, String title, String body) {
        RepoSlug repo = parseRepo(repositoryUrl);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        HttpRequest request = requestBuilder(repo.issuesUri(""), accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                .build();
        return toIssue(readSuccessful(send(request), "Unable to create GitHub issue"));
    }

    @Override
    public GithubIssue updateIssueBody(String repositoryUrl, String accessToken, int issueNumber, String body) {
        RepoSlug repo = parseRepo(repositoryUrl);
        Map<String, Object> payload = Map.of("body", body);
        HttpRequest request = requestBuilder(repo.issueUri(issueNumber), accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                .build();
        return toIssue(readSuccessful(send(request), "Unable to update GitHub issue"));
    }

    @Override
    public List<String> addAssignees(String repositoryUrl, String accessToken, int issueNumber, List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        RepoSlug repo = parseRepo(repositoryUrl);
        Map<String, Object> payload = Map.of("assignees", usernames);
        HttpRequest request = requestBuilder(repo.assigneesUri(issueNumber), accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                .build();
        JsonNode response = readSuccessful(send(request), "Unable to assign GitHub issue collaborators");
        List<String> accepted = new ArrayList<>();
        for (JsonNode assignee : response.path("assignees")) {
            String login = assignee.path("login").asText("").trim();
            if (!login.isEmpty()) {
                accepted.add(login);
            }
        }
        return accepted;
    }

    private HttpRequest.Builder requestBuilder(URI uri, String accessToken) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to contact GitHub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("GitHub request was interrupted", e);
        }
    }

    private JsonNode readSuccessful(HttpResponse<String> response, String fallback) {
        if (response.statusCode() >= 400) {
            throw githubException(response.statusCode(), fallback);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("GitHub returned an invalid response", e);
        }
    }

    private IllegalArgumentException githubException(int status, String fallback) {
        String message = switch (status) {
            case 401 -> "Project owner must reconnect GitHub";
            case 403 -> "Project owner does not have permission to manage repository issues";
            case 404 -> "GitHub repository was not found or is not accessible";
            case 410 -> "GitHub Issues are disabled for this repository";
            case 422 -> "GitHub rejected the issue data";
            default -> fallback + " (HTTP " + status + ")";
        };
        return new IllegalArgumentException(message);
    }

    private GithubIssue toIssue(JsonNode node) {
        Long id = node.path("id").isNumber() ? node.path("id").asLong() : null;
        Integer number = node.path("number").isNumber() ? node.path("number").asInt() : null;
        String url = node.path("html_url").asText("");
        String state = node.path("state").asText("open").toLowerCase(Locale.ROOT);
        if (id == null || number == null || url.isBlank()) {
            throw new IllegalArgumentException("GitHub issue response is incomplete");
        }
        return new GithubIssue(id, number, url, state);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to build GitHub issue request", e);
        }
    }

    private RepoSlug parseRepo(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("Project repository is required");
        }
        URI uri;
        try {
            uri = URI.create(repositoryUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Project repository must be a valid GitHub URL");
        }
        if (!"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Project repository must be a GitHub URL");
        }
        String[] parts = uri.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Project repository must identify an owner and repository");
        }
        String repo = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
        return new RepoSlug(parts[0], repo);
    }

    private record RepoSlug(String owner, String repo) {
        URI issuesUri(String query) {
            return URI.create(GITHUB_API_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues" + query);
        }

        URI issueUri(int number) {
            return URI.create(issuesUri("") + "/" + number);
        }

        URI assigneesUri(int number) {
            return URI.create(issueUri(number) + "/assignees");
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }
}
