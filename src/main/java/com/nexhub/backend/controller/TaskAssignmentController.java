package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentRequest;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentUpdateRequest;
import com.nexhub.backend.service.TaskAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/task-assignments")
@RequiredArgsConstructor
public class TaskAssignmentController {
    private final TaskAssignmentService taskAssignmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("List of task assignments", taskAssignmentService.getAllAssignments()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskAssignmentResponse>> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task assignment found", taskAssignmentService.getAssignmentById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponse>>> getByTask(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task assignments", taskAssignmentService.getAssignmentsByTask(taskId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponse>>> getByUser(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User task assignments", taskAssignmentService.getAssignmentsByUser(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskAssignmentResponse>> create(@RequestBody TaskAssignmentRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Task assignment created correctly", taskAssignmentService.createAssignment(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/updateassignment")
    public ResponseEntity<ApiResponse<TaskAssignmentResponse>> update(@RequestBody TaskAssignmentUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task assignment updated correctly", taskAssignmentService.updateAssignment(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<TaskAssignmentResponse>> delete(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task assignment deleted successfully", taskAssignmentService.deleteAssignment(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
