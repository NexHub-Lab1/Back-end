package com.nexhub.backend.dto.dashboard;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.UserDetailsResponse;
import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionResponse;

import java.util.List;

public record ProfileDashboardDTO(
        UserDetailsResponse userDetails,
        List<ProjectLookupDTO> projectLookups,
        UserStatsDTO stats,
        PagedResponse<ProjectResponse> projects,
        PagedResponse<TaskResponse> tasks,
        PagedResponse<TaskAssignmentResponse> assignments,
        PagedResponse<TaskSubmissionResponse> submissions,
        PagedResponse<TaskSubmissionResponse> toReview
) {}
