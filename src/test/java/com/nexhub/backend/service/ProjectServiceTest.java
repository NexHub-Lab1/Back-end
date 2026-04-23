package com.nexhub.backend.service;

import com.nexhub.backend.dto.project.ProjectRequest;
import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.dto.project.ProjectUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ProjectService projectService;

    @Nested
    @DisplayName("createProject")
    class CreateProjectTests {

        @Test
        void createsProjectWithDefaultsAndResolvedTags() {
            User owner = sampleUser();
            Tag aiTag = sampleTag(1L, "AI");

            when(userRepository.findById(7L)).thenReturn(Optional.of(owner));
            when(tagRepository.findByNameIgnoreCase("AI")).thenReturn(Optional.of(aiTag));
            when(tagRepository.findByNameIgnoreCase("Open Source")).thenReturn(Optional.empty());
            when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project project = invocation.getArgument(0);
                setProjectId(project, 9L);
                return project;
            });

            ProjectResponse response = projectService.createProject(new ProjectRequest(
                    7L,
                    "NexHub",
                    "Platform for builders",
                    "https://github.com/nexhub/backend",
                    null,
                    Set.of("AI", "Open Source")
            ));

            assertThat(response.id()).isEqualTo(9L);
            assertThat(response.ownerId()).isEqualTo(7L);
            assertThat(response.status()).isEqualTo("active");
            assertThat(response.starsCount()).isZero();
            assertThat(response.completedTasksCount()).isZero();
            assertThat(response.tags()).containsExactlyInAnyOrder("AI", "Open Source");
        }
    }

    @Nested
    @DisplayName("getProjectsByTag")
    class FilterByTagTests {

        @Test
        void returnsOnlyProjectsMatchingTag() {
            Project project = sampleProject();
            when(projectRepository.findDistinctByTags_NameIgnoreCase("AI")).thenReturn(List.of(project));

            List<ProjectResponse> response = projectService.getProjectsByTag("AI");

            assertThat(response).hasSize(1);
            assertThat(response.get(0).name()).isEqualTo("NexHub");
        }
    }

    @Nested
    @DisplayName("addStar")
    class AddStarTests {

        @Test
        void initializesAndIncrementsStarsWhenMissing() {
            Project project = sampleProject();
            project.setStars_count(null);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(projectRepository.save(project)).thenReturn(project);

            ProjectResponse response = projectService.addStar(1L);

            assertThat(response.starsCount()).isEqualTo(1L);
            verify(projectRepository).save(project);
        }
    }

    @Nested
    @DisplayName("deleteProject")
    class DeleteProjectTests {

        @Test
        void deletesExistingProject() {
            Project project = sampleProject();
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(taskRepository.existsByProject_Id(1L)).thenReturn(false);

            ProjectResponse response = projectService.deleteProject(1L);

            assertThat(response.id()).isEqualTo(1L);
            verify(projectRepository).delete(project);
        }

        @Test
        void rejectsDeleteWhenProjectHasTasks() {
            Project project = sampleProject();
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(taskRepository.existsByProject_Id(1L)).thenReturn(true);

            assertThatThrownBy(() -> projectService.deleteProject(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Project has tasks and cannot be deleted. Archive it instead.");
        }

        @Test
        void archivesExistingProject() {
            Project project = sampleProject();
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(projectRepository.save(project)).thenReturn(project);

            ProjectResponse response = projectService.archiveProject(1L);

            assertThat(response.status()).isEqualTo("archived");
            verify(projectRepository).save(project);
        }
    }

    @Nested
    @DisplayName("updateProject")
    class UpdateProjectTests {

        @Test
        void updatesFieldsAndSavesOnce() {
            Project project = sampleProject();
            Tag tag = sampleTag(2L, "Web3");

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(tagRepository.findByNameIgnoreCase("Web3")).thenReturn(Optional.of(tag));
            when(projectRepository.save(project)).thenReturn(project);

            ProjectResponse response = projectService.updateProject(new ProjectUpdateRequest(
                    1L,
                    "NexHub API",
                    "Updated description",
                    "https://github.com/nexhub/api",
                    "paused",
                    Set.of("Web3")
            ));

            assertThat(response.name()).isEqualTo("NexHub API");
            assertThat(response.description()).isEqualTo("Updated description");
            assertThat(response.githubRepo()).isEqualTo("https://github.com/nexhub/api");
            assertThat(response.status()).isEqualTo("paused");
            assertThat(response.tags()).containsExactly("Web3");
        }

        @Test
        void rejectsMissingNameOnCreate() {
            assertThatThrownBy(() -> projectService.createProject(new ProjectRequest(
                    7L,
                    " ",
                    "Platform for builders",
                    "https://github.com/nexhub/backend",
                    "active",
                    Set.of()
            ))).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Project name is required");
        }

        @Test
        void rejectsInvalidGithubRepositoryUrlOnCreate() {
            assertThatThrownBy(() -> projectService.createProject(new ProjectRequest(
                    7L,
                    "NexHub",
                    "Platform for builders",
                    "https://gitlab.com/nexhub/backend",
                    "active",
                    Set.of()
            ))).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Project repository must be a valid GitHub repository URL");
        }
    }

    private static User sampleUser() {
        User user = new User();
        setUserId(user, 7L);
        user.setUsername("manu");
        user.setEmail("manu@nexhub.dev");
        return user;
    }

    private static Tag sampleTag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private static Project sampleProject() {
        Project project = new Project();
        setProjectId(project, 1L);
        project.setOwner(sampleUser());
        project.setName("NexHub");
        project.setDescription("Platform for builders");
        project.setGithubRepo("https://github.com/nexhub/backend");
        project.setStatus("active");
        project.setCreated_at(Date.valueOf("2026-04-01"));
        project.setUpdated_at(Date.valueOf("2026-04-01"));
        project.setLast_active_at(Date.valueOf("2026-04-01"));
        project.setCompleted_tasks_count(0L);
        project.setStars_count(10L);
        return project;
    }

    private static void setProjectId(Project project, Long id) {
        try {
            var field = Project.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(project, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
