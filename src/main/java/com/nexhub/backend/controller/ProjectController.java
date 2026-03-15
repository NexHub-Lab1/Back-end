package com.nexhub.backend.controller;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    // GET http://localhost:8080/api/projects
    @GetMapping
    public List<Project> getAll() {
        return projectRepository.findAll();
    }

    // POST http://localhost:8080/api/projects
    @PostMapping
    public Project create(@RequestBody Project project) {
        return projectRepository.save(project);
    }
}