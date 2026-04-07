package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Project>>> getAll() {
        return ResponseEntity
                .status(200)
                .body(
                        ApiResponse.success(
                                "List of projects",
                                projectRepository.findAll()
                        )
                );
    }

    @GetMapping("/findbytag")
    public ResponseEntity<ApiResponse<List<Project>>> getProjectsByTag(@RequestParam String tag) {
        return ResponseEntity.status(200).body(
                ApiResponse.success("List of projects", projectRepository.findAll())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Project>> create(@RequestBody Project request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("created correctly", projectRepository.save(request))
                );
    }

    @PostMapping("/addstar")
    public ResponseEntity<ApiResponse<Project>> star(@RequestBody Long id) {
        Optional<Project> project = projectRepository.findById(id);
        if (project.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Project not found"));

        project.get()
                .setStars_count(project.get().getStars_count() + 1);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("star correctly", projectRepository.save(project.get()))
        );
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Project>> delete(@RequestBody Long id) {
        Optional<Project> p = projectRepository.findById(id);
        return p.map(project -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("Deleted successfully", project)
        )).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Project not found")));
    }

    @PostMapping("/updateproject")
    public ResponseEntity<ApiResponse<Project>> update(@RequestBody Project request) {
        if (projectRepository.findById(request.getId()).isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Project not found"));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("updated correctly", projectRepository.save(projectRepository.save(request)))
        );
    }
}