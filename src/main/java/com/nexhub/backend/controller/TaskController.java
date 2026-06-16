package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.task.FeaturedTaskResponse;
import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.task.TaskUpdateRequest;
import com.nexhub.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("List of tasks", taskService.getAllTasks(search, status, pageable)));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<PagedResponse<FeaturedTaskResponse>>> getFeatured(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Featured tasks", taskService.getFeaturedTasks(userId, pageable)));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> getByOwner(
            @PathVariable Long ownerId,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Owner tasks", taskService.getTasksByOwner(ownerId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task found", taskService.getTaskById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> getByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 9) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("List of tasks", taskService.getTasksByProject(projectId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Task created correctly", taskService.createTask(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<TaskResponse>> delete(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Deleted successfully", taskService.deleteTask(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<TaskResponse>> cancel(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Cancelled successfully", taskService.cancelTask(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/updatetask")
    public ResponseEntity<ApiResponse<TaskResponse>> update(@Valid @RequestBody TaskUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Updated correctly", taskService.updateTask(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
