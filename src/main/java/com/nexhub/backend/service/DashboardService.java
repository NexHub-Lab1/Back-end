package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.UserDetailsResponse;
import com.nexhub.backend.dto.dashboard.ProfileDashboardDTO;
import com.nexhub.backend.dto.dashboard.ProjectLookupDTO;
import com.nexhub.backend.dto.dashboard.UserStatsDTO;
import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionResponse;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskSubmissionRepository submissionRepository;

    private static final int INITIAL_PAGE_SIZE = 6;

    @Transactional(readOnly = true)
    public ProfileDashboardDTO getProfileDashboard(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        UserDetailsResponse userDetails = UserDetailsResponse.fromUser(user);

        // 1. Project Lookups (for dropdowns)
        List<ProjectLookupDTO> projectLookups = projectRepository.findByOwner_Id(userId).stream()
                .filter(p -> !"archived".equalsIgnoreCase(p.getStatus()))
                .map(p -> new ProjectLookupDTO(p.getId(), p.getName()))
                .toList();

        // 2. User Stats
        UserStatsDTO stats = new UserStatsDTO(
                user.getTotal_points() != null ? user.getTotal_points() : 0,
                user.getReputation_score() != null ? user.getReputation_score() : 0,
                user.getStreak_day() != null ? user.getStreak_day() : 0
        );

        Pageable firstPage = PageRequest.of(0, INITIAL_PAGE_SIZE);
        Pageable firstPageDesc = PageRequest.of(0, INITIAL_PAGE_SIZE, Sort.by("id").descending());

        // 3. First page of Projects
        PagedResponse<ProjectResponse> projects = PagedResponse.fromPage(
                projectRepository.findByOwner_Id(userId, firstPage)
                        .map(ProjectResponse::fromProject)
        );

        // 4. First page of Tasks (owned by user)
        PagedResponse<TaskResponse> tasks = PagedResponse.fromPage(
                taskRepository.findByProjectOwnerId(userId, firstPage)
                        .map(TaskResponse::fromTask)
        );

        // 5. First page of Assignments (assigned to user)
        PagedResponse<TaskAssignmentResponse> assignments = PagedResponse.fromPage(
                assignmentRepository.findByUserId(userId, PageRequest.of(0, INITIAL_PAGE_SIZE, Sort.by("assignedAt").descending()))
                        .map(TaskAssignmentResponse::fromTaskAssignment)
        );

        // 6. First page of Submissions (made by user)
        PagedResponse<TaskSubmissionResponse> submissions = PagedResponse.fromPage(
                submissionRepository.findByUserId(userId, PageRequest.of(0, INITIAL_PAGE_SIZE, Sort.by("submittedAt").descending()))
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );

        // 7. First page of Submissions to Review (on user's projects)
        PagedResponse<TaskSubmissionResponse> toReview = PagedResponse.fromPage(
                submissionRepository.findByProjectOwnerIdAndStatus(userId, "submitted", PageRequest.of(0, INITIAL_PAGE_SIZE, Sort.by("submittedAt").descending()))
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );

        return new ProfileDashboardDTO(
                userDetails,
                projectLookups,
                stats,
                projects,
                tasks,
                assignments,
                submissions,
                toReview
        );
    }
}
