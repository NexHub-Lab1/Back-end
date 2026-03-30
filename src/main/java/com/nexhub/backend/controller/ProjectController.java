package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    // GET http://localhost:8080/api/projects
    @GetMapping
    public ResponseEntity<ApiResponse<Project>> getAll() {
        return projectRepository.findAll();
    }

    // POST http://localhost:8080/api/projects
    @PostMapping
    public ResponseEntity<ApiResponse<Project>> create(@RequestBody ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setGithubRepo(request.gitRepo());
        project.setStatus(request.status());
        project.setUpdated_at(new Date);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("created correctly", projectRepository.save())
                );
    }
}