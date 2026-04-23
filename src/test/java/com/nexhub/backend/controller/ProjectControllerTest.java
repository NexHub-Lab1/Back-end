package com.nexhub.backend.controller;

import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Date;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(projectService)).build();
    }

    @Nested
    @DisplayName("GET /api/projects")
    class GetAllProjectsTests {

        @Test
        void returnsListOfProjects() throws Exception {
            when(projectService.getAllProjects()).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].name").value("NexHub"))
                    .andExpect(jsonPath("$.data[0].ownerUsername").value("manu"));
        }
    }

    @Nested
    @DisplayName("GET /api/projects/{id}")
    class GetProjectByIdTests {

        @Test
        void returnsProjectWhenItExists() throws Exception {
            when(projectService.getProjectById(1L)).thenReturn(sampleResponse());

            mockMvc.perform(get("/api/projects/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        void returnsNotFoundWhenProjectDoesNotExist() throws Exception {
            when(projectService.getProjectById(99L)).thenThrow(new NoSuchElementException("Project not found"));

            mockMvc.perform(get("/api/projects/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Project not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/projects/findbytag")
    class GetProjectsByTagTests {

        @Test
        void returnsFilteredProjects() throws Exception {
            when(projectService.getProjectsByTag("AI")).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/projects/findbytag").param("tag", "AI"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].tags[0]").value("AI"));
        }
    }

    @Nested
    @DisplayName("POST /api/projects")
    class CreateProjectTests {

        @Test
        void returnsCreatedProject() throws Exception {
            when(projectService.createProject(org.mockito.ArgumentMatchers.any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "ownerId": 7,
                                      "name": "NexHub",
                                      "description": "Platform for builders",
                                      "githubRepo": "https://github.com/nexhub/backend",
                                      "status": "active",
                                      "tags": ["AI", "Open Source"]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Project created correctly"))
                    .andExpect(jsonPath("$.data.name").value("NexHub"));
        }
    }

    @Nested
    @DisplayName("POST /api/projects/addstar")
    class AddStarTests {

        @Test
        void addsStarWhenProjectExists() throws Exception {
            ProjectResponse response = sampleResponse();
            response = new ProjectResponse(
                    response.id(),
                    response.ownerId(),
                    response.ownerUsername(),
                    response.name(),
                    response.description(),
                    response.githubRepo(),
                    response.status(),
                    response.createdAt(),
                    response.updatedAt(),
                    response.lastActiveAt(),
                    response.completedTasksCount(),
                    11L,
                    response.contributorCount(),
                    response.tags()
            );

            when(projectService.addStar(1L)).thenReturn(response);

            mockMvc.perform(post("/api/projects/addstar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Star added correctly"))
                    .andExpect(jsonPath("$.data.starsCount").value(11));
        }
    }

    @Nested
    @DisplayName("POST /api/projects/delete")
    class DeleteProjectTests {

        @Test
        void deletesExistingProject() throws Exception {
            when(projectService.deleteProject(1L)).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/projects/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Deleted successfully"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/projects/updateproject")
    class UpdateProjectTests {

        @Test
        void updatesProject() throws Exception {
            ProjectResponse updated = new ProjectResponse(
                    1L,
                    7L,
                    "manu",
                    "NexHub API",
                    "Platform for builders",
                    "https://github.com/nexhub/backend",
                    "active",
                    Date.valueOf("2026-04-01"),
                    Date.valueOf("2026-04-02"),
                    Date.valueOf("2026-04-02"),
                    0L,
                    10L,
                    0,
                    List.of("AI", "Open Source")
            );
            when(projectService.updateProject(org.mockito.ArgumentMatchers.any())).thenReturn(updated);

            mockMvc.perform(post("/api/projects/updateproject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "id": 1,
                                      "name": "NexHub API",
                                      "description": "Platform for builders",
                                      "githubRepo": "https://github.com/nexhub/backend",
                                      "status": "active",
                                      "tags": ["AI", "Open Source"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Updated correctly"))
                    .andExpect(jsonPath("$.data.name").value("NexHub API"));
        }
    }

    private static ProjectResponse sampleResponse() {
        return new ProjectResponse(
                1L,
                7L,
                "manu",
                "NexHub",
                "Platform for builders",
                "https://github.com/nexhub/backend",
                "active",
                Date.valueOf("2026-04-01"),
                Date.valueOf("2026-04-01"),
                Date.valueOf("2026-04-01"),
                0L,
                10L,
                0,
                List.of("AI", "Open Source")
        );
    }
}
