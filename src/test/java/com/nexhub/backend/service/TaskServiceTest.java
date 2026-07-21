package com.nexhub.backend.service;

import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskType;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskInvitationRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private TagRepository tagRepository;
    @Mock private TaskAssignmentRepository taskAssignmentRepository;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;
    @Mock private TaskInvitationRepository taskInvitationRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks private TaskService taskService;

    @Test
    void createTaskPersistsDesignType() {
        Project project = new Project();
        project.setName("Design system");
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = taskService.createTask(new TaskRequest(
                7L,
                "Design onboarding flow",
                "Create the complete onboarding experience in Figma.",
                "A shareable Figma design link",
                new BigDecimal("25000"),
                "ARS",
                Date.valueOf("2026-08-15"),
                "OPEN",
                2,
                0,
                false,
                Set.of(),
                "DESIGN"
        ));

        assertThat(response.taskType()).isEqualTo("DESIGN");
    }

    @Test
    void visualAliasMapsToDesignAndUnknownTypesFail() {
        assertThat(TaskType.fromNullable("visual")).isEqualTo(TaskType.DESIGN);
        assertThatThrownBy(() -> TaskType.fromNullable("marketing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task type must be DEVELOPMENT or DESIGN");
    }
}
