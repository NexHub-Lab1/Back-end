package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.task.FeaturedTaskResponse;
import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.task.TaskUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getAllTasks(String search, String status, Pageable pageable) {
        return PagedResponse.fromPage(
                taskRepository.searchTasks(search, status, pageable).map(TaskResponse::fromTask)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<FeaturedTaskResponse> getFeaturedTasks(Long userId, Pageable pageable) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);

        List<FeaturedTaskResponse> rankedTasks = taskRepository.findFeaturedCandidates().stream()
                .map(task -> scoreFeaturedTask(task, user))
                .sorted(Comparator
                        .comparing(FeaturedTaskResponse::recommendationScore, Comparator.reverseOrder())
                        .thenComparing(response -> response.task().createdAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(response -> response.task().id(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return pageFeaturedTasks(rankedTasks, pageable);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        return TaskResponse.fromTask(findExistingTask(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getTasksByProject(Long projectId, Pageable pageable) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        return PagedResponse.fromPage(
                taskRepository.findByProjectId(projectId, pageable)
                        .map(TaskResponse::fromTask)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getTasksByOwner(Long ownerId, Pageable pageable) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner id is required");
        }

        return PagedResponse.fromPage(
                taskRepository.findByProjectOwnerId(ownerId, pageable)
                        .map(TaskResponse::fromTask)
        );
    }

    public TaskResponse createTask(TaskRequest request) {
        validateCreateRequest(request);

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NoSuchElementException("Project not found"));

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description().trim());
        task.setDeliverables(normalizeOptionalText(request.deliverables()));
        task.setRewardAmount(request.rewardAmount().stripTrailingZeros());
        task.setRewardCurrency(normalizeCurrency(request.rewardCurrency()));
        task.setDeadline(request.deadline());
        task.setStatus(normalizeStatus(request.status()));
        task.setMaxAttempts(normalizeMaxAttempts(request.maxAttempts()));
        task.setMinReputation(request.minReputation() != null ? request.minReputation() : 0);
        task.setCollaborative(request.collaborative() != null ? request.collaborative() : false);
        task.setCreated_at(now());
        task.setUpdated_at(now());
        task.setRecommendedSkills(resolveTags(request.recommendedSkills()));

        return TaskResponse.fromTask(taskRepository.save(task));
    }

    public TaskResponse updateTask(TaskUpdateRequest request) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        Task task = findExistingTask(request.id());
        validateFundingLockedFields(task, request);

        if (request.projectId() != null) {
            Project project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new NoSuchElementException("Project not found"));
            task.setProject(project);
        }
        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            task.setDescription(request.description().trim());
        }
        if (request.deliverables() != null) {
            task.setDeliverables(normalizeOptionalText(request.deliverables()));
        }
        if (request.rewardAmount() != null) {
            task.setRewardAmount(request.rewardAmount().stripTrailingZeros());
        }
        if (request.rewardCurrency() != null && !request.rewardCurrency().isBlank()) {
            task.setRewardCurrency(normalizeCurrency(request.rewardCurrency()));
        }
        if (request.deadline() != null) {
            task.setDeadline(request.deadline());
        }
        if (request.status() != null && !request.status().isBlank()) {
            task.setStatus(normalizeStatus(request.status()));
        }
        if (request.maxAttempts() != null) {
            task.setMaxAttempts(normalizeMaxAttempts(request.maxAttempts()));
        }
        if (request.minReputation() != null) {
            task.setMinReputation(request.minReputation());
        }
        if (request.collaborative() != null) {
            task.setCollaborative(request.collaborative());
        }
        if (request.recommendedSkills() != null) {
            task.setRecommendedSkills(resolveTags(request.recommendedSkills()));
        }

        validateTaskFields(task.getTitle(), task.getDescription(), task.getRewardAmount());
        task.setUpdated_at(now());

        return TaskResponse.fromTask(taskRepository.save(task));
    }

    public TaskResponse deleteTask(Long id) {
        Task task = findExistingTask(id);

        if (taskAssignmentRepository.existsByTask_Id(task.getId()) || taskSubmissionRepository.existsByTask_Id(task.getId())) {
            throw new IllegalArgumentException("Task has assignments or submissions and cannot be deleted. Cancel it instead.");
        }
        if (paymentService.taskHasPaymentHistory(task)) {
            throw new IllegalArgumentException("Task has payment history and cannot be deleted. Cancel it instead.");
        }

        TaskResponse response = TaskResponse.fromTask(task);
        taskRepository.delete(task);
        return response;
    }

    public TaskResponse cancelTask(Long id) {
        Task task = findExistingTask(id);
        paymentService.refundTaskEscrow(task, "Task cancelled");
        task.setStatus("cancelled");
        task.setUpdated_at(now());

        return TaskResponse.fromTask(taskRepository.save(task));
    }

    private FeaturedTaskResponse scoreFeaturedTask(Task task, User user) {
        int score = 40;
        boolean eligible = true;
        List<String> reasons = new ArrayList<>();
        List<String> matchedSkills = new ArrayList<>();

        score += scoreStatus(task, reasons);
        score += scoreFunding(task, reasons);
        score += scoreReward(task, reasons);
        score += scoreDeadline(task, reasons);
        score += scoreFreshness(task, reasons);
        score += scoreTaskQuality(task, reasons);
        score += scoreProjectContext(task, reasons);

        User owner = task.getProject() == null ? null : task.getProject().getOwner();
        if (user != null && owner != null && owner.getId() != null && owner.getId().equals(user.getId())) {
            score -= 35;
            addReason(reasons, "Your own project");
        }

        if (user != null && task.getId() != null) {
            if (taskAssignmentRepository.existsByTask_IdAndUser_Id(task.getId(), user.getId())) {
                score -= 70;
                eligible = false;
                addReason(reasons, "Already assigned to you");
            }
            if (taskSubmissionRepository.existsByTask_IdAndUser_Id(task.getId(), user.getId())) {
                score -= 80;
                eligible = false;
                addReason(reasons, "Already submitted");
            }
        }

        ReputationScore reputationScore = scoreReputation(task, user, reasons);
        score += reputationScore.score();
        eligible = eligible && reputationScore.eligible();
        score += scoreSkills(task, user, reasons, matchedSkills);

        if (reasons.isEmpty()) {
            addReason(reasons, user == null ? "Strong platform task" : "Recommended for your profile");
        }

        return new FeaturedTaskResponse(
                TaskResponse.fromTask(task),
                Math.max(0, score),
                reasons,
                matchedSkills,
                eligible
        );
    }

    private PagedResponse<FeaturedTaskResponse> pageFeaturedTasks(List<FeaturedTaskResponse> rankedTasks, Pageable pageable) {
        int total = rankedTasks.size();
        if (pageable == null || pageable.isUnpaged()) {
            return new PagedResponse<>(
                    rankedTasks,
                    0,
                    total,
                    total,
                    total == 0 ? 0 : 1,
                    true,
                    true,
                    false,
                    false
            );
        }

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        return new PagedResponse<>(
                rankedTasks.subList(fromIndex, toIndex),
                page,
                size,
                total,
                totalPages,
                page == 0,
                page >= totalPages - 1,
                page < totalPages - 1,
                page > 0
        );
    }

    private int scoreStatus(Task task, List<String> reasons) {
        String status = normalizeComparable(task.getStatus());
        if ("open".equals(status) || "hiring".equals(status)) {
            addReason(reasons, "Open now");
            return 20;
        }
        if ("in_progress".equals(status) || "review".equals(status)) {
            return -20;
        }
        return 0;
    }

    private int scoreFunding(Task task, List<String> reasons) {
        String fundingStatus = normalizeComparable(task.getFundingStatus());
        if ("funded".equals(fundingStatus)) {
            addReason(reasons, "Funded");
            return 28;
        }
        if ("pending".equals(fundingStatus)) {
            addReason(reasons, "Funding pending");
            return 10;
        }
        return 0;
    }

    private int scoreReward(Task task, List<String> reasons) {
        BigDecimal rewardAmount = task.getRewardAmount();
        if (rewardAmount == null || rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (rewardAmount.compareTo(BigDecimal.valueOf(500)) >= 0) {
            addReason(reasons, "High reward");
            return 14;
        }
        if (rewardAmount.compareTo(BigDecimal.valueOf(100)) >= 0) {
            addReason(reasons, "Good reward");
            return 9;
        }
        return 5;
    }

    private int scoreDeadline(Task task, List<String> reasons) {
        if (task.getDeadline() == null) {
            addReason(reasons, "Flexible deadline");
            return 5;
        }

        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline().toLocalDate());
        if (daysUntilDeadline < 0) {
            return -70;
        }
        if (daysUntilDeadline <= 3) {
            addReason(reasons, "Due soon");
            return 2;
        }
        if (daysUntilDeadline <= 14) {
            addReason(reasons, "Healthy deadline");
            return 12;
        }
        if (daysUntilDeadline <= 45) {
            addReason(reasons, "Enough time");
            return 8;
        }
        return 4;
    }

    private int scoreFreshness(Task task, List<String> reasons) {
        if (task.getCreated_at() == null) {
            return 0;
        }

        long ageInDays = ChronoUnit.DAYS.between(task.getCreated_at().toLocalDate(), LocalDate.now());
        if (ageInDays <= 7) {
            addReason(reasons, "New task");
            return 8;
        }
        if (ageInDays <= 30) {
            return 4;
        }
        return 0;
    }

    private int scoreTaskQuality(Task task, List<String> reasons) {
        int score = 0;
        if (task.getDescription() != null && task.getDescription().trim().length() >= 80) {
            score += 4;
        }
        if (task.getDeliverables() != null && task.getDeliverables().trim().length() >= 20) {
            score += 5;
        }
        if (task.getRecommendedSkills() != null && !task.getRecommendedSkills().isEmpty()) {
            score += 3;
        }
        if (task.getMaxAttempts() != null && task.getMaxAttempts() > 1) {
            score += 2;
        }
        if (score >= 7) {
            addReason(reasons, "Clear scope");
        }
        return score;
    }

    private int scoreProjectContext(Task task, List<String> reasons) {
        Project project = task.getProject();
        if (project == null) {
            return 0;
        }

        int score = 0;
        if (project.getGithubRepo() != null && !project.getGithubRepo().isBlank()) {
            score += 4;
        }
        if ("connected".equalsIgnoreCase(project.getGithubWebhookStatus())) {
            score += 4;
            addReason(reasons, "GitHub connected");
        }
        if (Boolean.TRUE.equals(task.getCollaborative())) {
            score += 4;
            addReason(reasons, "Collaborative");
        }
        return score;
    }

    private ReputationScore scoreReputation(Task task, User user, List<String> reasons) {
        int minReputation = task.getMinReputation() == null ? 0 : task.getMinReputation();
        if (user == null) {
            if (minReputation == 0) {
                addReason(reasons, "No reputation required");
                return new ReputationScore(10, true);
            }
            return new ReputationScore(Math.max(0, 10 - (minReputation / 50)), true);
        }

        int userReputation = user.getReputation_score() == null ? 0 : user.getReputation_score();
        if (minReputation == 0) {
            addReason(reasons, "No reputation required");
            return new ReputationScore(12, true);
        }
        if (userReputation >= minReputation) {
            addReason(reasons, "You meet the reputation requirement");
            return new ReputationScore(12, true);
        }

        addReason(reasons, "Needs " + minReputation + " reputation");
        return new ReputationScore(-45, false);
    }

    private int scoreSkills(Task task, User user, List<String> reasons, List<String> matchedSkills) {
        Set<String> taskSkills = normalizedTagNames(task.getRecommendedSkills());
        if (taskSkills.isEmpty()) {
            addReason(reasons, "No specific skill required");
            return user == null ? 6 : 8;
        }

        if (user == null) {
            addReason(reasons, "In-demand skills");
            return 5;
        }

        Set<String> userSkills = normalizedTagNames(user.getSkills());
        for (String taskSkill : taskSkills) {
            if (userSkills.contains(taskSkill)) {
                matchedSkills.add(taskSkill);
            }
        }

        if (matchedSkills.isEmpty()) {
            addReason(reasons, "Builds new skills");
            return -8;
        }

        List<String> visibleMatches = matchedSkills.stream()
                .limit(2)
                .map(this::humanizeSkill)
                .toList();
        for (String visibleMatch : visibleMatches) {
            addReason(reasons, "Matches " + visibleMatch);
        }
        if (matchedSkills.size() > visibleMatches.size()) {
            addReason(reasons, "Matches " + matchedSkills.size() + " skills");
        }
        return Math.min(36, matchedSkills.size() * 12);
    }

    private Set<String> normalizedTagNames(Set<Tag> tags) {
        Set<String> names = new HashSet<>();
        if (tags == null) {
            return names;
        }

        for (Tag tag : tags) {
            if (tag == null || tag.getName() == null || tag.getName().isBlank()) {
                continue;
            }
            names.add(normalizeComparable(tag.getName()));
        }
        return names;
    }

    private String normalizeComparable(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String humanizeSkill(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.replace('_', ' ').split("\\s+");
        List<String> titleCased = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            titleCased.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", titleCased);
    }

    private void addReason(List<String> reasons, String reason) {
        if (reason == null || reason.isBlank() || reasons.contains(reason) || reasons.size() >= 5) {
            return;
        }
        reasons.add(reason);
    }

    private Task findExistingTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        return taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
    }

    private void validateCreateRequest(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task data is required");
        }
        if (request.projectId() == null) {
            throw new IllegalArgumentException("Project id is required");
        }
        validateTaskFields(request.title(), request.description(), request.rewardAmount());
    }

    private void validateFundingLockedFields(Task task, TaskUpdateRequest request) {
        if (!paymentService.taskHasLockedFunding(task)) {
            return;
        }

        boolean changesProject = request.projectId() != null
                && !request.projectId().equals(task.getProject().getId());
        boolean changesRewardAmount = request.rewardAmount() != null
                && request.rewardAmount().compareTo(task.getRewardAmount()) != 0;
        boolean changesRewardCurrency = request.rewardCurrency() != null
                && !normalizeCurrency(request.rewardCurrency()).equals(task.getRewardCurrency());

        if (changesProject || changesRewardAmount || changesRewardCurrency) {
            throw new IllegalArgumentException("Task project and reward cannot be changed after funding starts");
        }
    }

    private void validateTaskFields(String title, String description, BigDecimal rewardAmount) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Task description is required");
        }
        if (rewardAmount == null || rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Task reward amount must be greater than zero");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCurrency(String rewardCurrency) {
        if (rewardCurrency == null || rewardCurrency.isBlank()) {
            return "ARS";
        }
        return rewardCurrency.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? "open" : status.trim().toLowerCase();
    }

    private Integer normalizeMaxAttempts(Integer maxAttempts) {
        if (maxAttempts == null || maxAttempts < 1) {
            return 1;
        }
        return maxAttempts;
    }

    private Set<Tag> resolveTags(Set<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Tag> resolvedTags = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag == null) {
                continue;
            }

            String normalizedTag = rawTag.trim();
            if (normalizedTag.isEmpty()) {
                continue;
            }

            Tag tag = tagRepository.findByNameIgnoreCase(normalizedTag)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(normalizedTag);
                        return tagRepository.save(newTag);
                    });
            resolvedTags.add(tag);
        }

        return resolvedTags;
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }

    private record ReputationScore(int score, boolean eligible) {
    }
}
