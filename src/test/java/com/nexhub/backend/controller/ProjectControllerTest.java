package com.nexhub.backend.controller;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.UserRepository;
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

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Setup manual del controller inyectando los mocks
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(projectRepository, userRepository)).build();
    }

    @Nested
    @DisplayName("GET /api/projects")
    class GetAllProjectsTests {

        @Test
        @DisplayName("Debe retornar la lista de proyectos exitosamente")
        void returnsListOfProjects() throws Exception {
            Project project = sampleProject();
            when(projectRepository.findAll()).thenReturn(Collections.singletonList(project));

            mockMvc.perform(get("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data[0].name").value("NexHub"));
        }
    }

    @Nested
    @DisplayName("POST /api/projects")
    class CreateProjectTests {

        @Test
        @DisplayName("Debe crear un proyecto y retornar status ACCEPTED")
        void returnsCreatedProject() throws Exception {
            Project project = sampleProject();
            when(projectRepository.save(any(Project.class))).thenReturn(project);

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "NexHub",
                                      "description": "Platform for builders"
                                    }
                                    """))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("created correctly"));
        }
    }

    @Nested
    @DisplayName("POST /api/projects/addstar")
    class AddStarTests {

        @Test
        @DisplayName("Debe sumar una estrella cuando el proyecto existe")
        void addsStarWhenProjectExists() throws Exception {
            Project project = sampleProject();
            project.setStars_count(10L);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenReturn(project);

            mockMvc.perform(post("/api/projects/addstar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("1"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("star correctly"));
        }

        @Test
        @DisplayName("Debe retornar error cuando el proyecto no existe")
        void returnsErrorWhenProjectNotFound() throws Exception {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/projects/addstar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Project not found"));
        }
    }

    // Helper para crear un proyecto de prueba
    private static Project sampleProject() {
        Project project = new Project();
        project.setName("NexHub");
        project.setDescription("Platform for builders");
        project.setStars_count(0L);
        return project;
    }
}