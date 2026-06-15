package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.project.ProjectRequest;
import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.dto.project.ProjectUpdateRequest;
import com.nexhub.backend.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProjectResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("List of projects", projectService.getAllProjects(search, status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Project found", projectService.getProjectById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/findbytag")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectResponse>>> getProjectsByTag(
            @RequestParam String tag,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("List of projects", projectService.getProjectsByTag(tag, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Project created correctly", projectService.createProject(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/addstar")
    public ResponseEntity<ApiResponse<ProjectResponse>> star(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Star added correctly", projectService.addStar(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<ProjectResponse>> delete(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Deleted successfully", projectService.deleteProject(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/archive")
    public ResponseEntity<ApiResponse<ProjectResponse>> archive(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Archived successfully", projectService.archiveProject(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/updateproject")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(@Valid @RequestBody ProjectUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Updated correctly", projectService.updateProject(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/owner/{owner}")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectResponse>>> getOwnedProjects(
            @PathVariable Long owner,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Projects", projectService.getProjectsByOwner(owner, pageable)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
