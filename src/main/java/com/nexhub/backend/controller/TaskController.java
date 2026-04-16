package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.task.TaskUpdateRequest;
import com.nexhub.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("List of tasks", taskService.getAllTasks()));
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
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getByProject(@PathVariable Long projectId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("List of tasks", taskService.getTasksByProject(projectId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@RequestBody TaskRequest request) {
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

    @PostMapping("/updatetask")
    public ResponseEntity<ApiResponse<TaskResponse>> update(@RequestBody TaskUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Updated correctly", taskService.updateTask(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
