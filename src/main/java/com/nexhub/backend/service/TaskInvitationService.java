package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.taskinvitation.TaskInvitationRequest;
import com.nexhub.backend.dto.taskinvitation.TaskInvitationResponse;
import com.nexhub.backend.event.TaskAssignmentCreatedEvent;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.TaskInvitation;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskInvitationRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskInvitationService {

    private final TaskInvitationRepository taskInvitationRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskInvitationResponse createInvitation(User sender, TaskInvitationRequest request) {
        if (request == null || request.taskId() == null || request.receiverId() == null) {
            throw new IllegalArgumentException("Task ID and Receiver ID are required");
        }

        if (sender.getId().equals(request.receiverId())) {
            throw new IllegalArgumentException("You cannot invite yourself");
        }

        Task task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new NoSuchElementException("Task not found"));

        User receiver = userRepository.findById(request.receiverId())
                .orElseThrow(() -> new NoSuchElementException("Invited user not found"));

        com.nexhub.backend.model.Project project = task.getProject();
        User owner = project != null ? project.getOwner() : null;
        if (owner != null && owner.getId() != null && owner.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Project owner cannot be invited to their own task");
        }

        // 1. Validate sender has an active assignment for the task
        List<TaskAssignment> taskAssignments = taskAssignmentRepository.findByTask_Id(task.getId());
        boolean senderHasActiveAssignment = taskAssignments.stream()
                .anyMatch(a -> a.getUser().getId().equals(sender.getId()) && "active".equalsIgnoreCase(a.getStatus()));

        if (!senderHasActiveAssignment) {
            throw new IllegalArgumentException("You must have an active assignment on this task to invite collaborators");
        }

        // 2. Validate receiver is not already assigned (active or completed)
        boolean receiverAlreadyAssigned = taskAssignments.stream()
                .anyMatch(a -> a.getUser().getId().equals(receiver.getId()) && !"cancelled".equalsIgnoreCase(a.getStatus()));

        if (receiverAlreadyAssigned) {
            throw new IllegalArgumentException("The user is already assigned or working on this task");
        }

        // 3. Validate no pending invitation exists for this receiver on this task
        boolean pendingInvitationExists = taskInvitationRepository.existsByTaskIdAndReceiverIdAndStatus(
                task.getId(), receiver.getId(), "pending"
        );

        if (pendingInvitationExists) {
            throw new IllegalArgumentException("A pending invitation already exists for this user on this task");
        }

        // 4. Validate receiver reputation
        int minRep = task.getMinReputation() != null ? task.getMinReputation() : 0;
        int userRep = receiver.getReputation_score() != null ? receiver.getReputation_score() : 0;
        if (userRep < minRep) {
            throw new IllegalArgumentException("The invited user's reputation score is too low for this task (Required: " + minRep + ")");
        }

        // 5. Validate task deadline has not passed
        if (task.getDeadline() != null && task.getDeadline().before(new Date(System.currentTimeMillis()))) {
            throw new IllegalArgumentException("Cannot invite collaborator: the task deadline has already passed");
        }

        // Create invitation
        TaskInvitation invitation = new TaskInvitation();
        invitation.setTask(task);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitation.setStatus("pending");
        invitation.setCreatedAt(new Date(System.currentTimeMillis()));

        TaskInvitation savedInvitation = taskInvitationRepository.save(invitation);

        // Send WebSocket/Notification
        notificationService.sendNotification(
                receiver,
                sender.getUsername() + " has invited you to collaborate on the task: " + task.getTitle(),
                "INFO",
                "/task/" + task.getId()
        );

        return TaskInvitationResponse.fromTaskInvitation(savedInvitation);
    }

    public TaskInvitationResponse acceptInvitation(User receiver, Long invitationId) {
        TaskInvitation invitation = taskInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NoSuchElementException("Invitation not found"));

        if (!invitation.getReceiver().getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }

        if (!"pending".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException("This invitation is no longer pending (Current status: " + invitation.getStatus() + ")");
        }

        // Find sender's active assignment to set as parent
        TaskAssignment senderAssignment = taskAssignmentRepository.findByTask_Id(invitation.getTask().getId()).stream()
                .filter(a -> a.getUser().getId().equals(invitation.getSender().getId()) && "active".equalsIgnoreCase(a.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The lead developer's assignment is no longer active"));

        // Update invitation status
        invitation.setStatus("accepted");
        TaskInvitation savedInvitation = taskInvitationRepository.save(invitation);

        // Create new assignment for collaborator
        TaskAssignment collaboratorAssignment = new TaskAssignment();
        collaboratorAssignment.setTask(invitation.getTask());
        collaboratorAssignment.setUser(receiver);
        collaboratorAssignment.setAssignedAt(new Date(System.currentTimeMillis()));
        collaboratorAssignment.setStatus("active");
        collaboratorAssignment.setAttemptsUsed(0);
        collaboratorAssignment.setParentAssignment(senderAssignment);

        taskAssignmentRepository.save(collaboratorAssignment);
        eventPublisher.publishEvent(new TaskAssignmentCreatedEvent(invitation.getTask().getId()));

        // Notify sender
        notificationService.sendNotification(
                invitation.getSender(),
                receiver.getUsername() + " has accepted your invitation to collaborate on: " + invitation.getTask().getTitle(),
                "SUCCESS",
                "/task/" + invitation.getTask().getId()
        );

        return TaskInvitationResponse.fromTaskInvitation(savedInvitation);
    }

    public TaskInvitationResponse rejectInvitation(User receiver, Long invitationId) {
        TaskInvitation invitation = taskInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NoSuchElementException("Invitation not found"));

        if (!invitation.getReceiver().getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }

        if (!"pending".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException("This invitation is no longer pending");
        }

        invitation.setStatus("rejected");
        TaskInvitation savedInvitation = taskInvitationRepository.save(invitation);

        // Notify sender
        notificationService.sendNotification(
                invitation.getSender(),
                receiver.getUsername() + " has declined your invitation to collaborate on: " + invitation.getTask().getTitle(),
                "INFO",
                "/task/" + invitation.getTask().getId()
        );

        return TaskInvitationResponse.fromTaskInvitation(savedInvitation);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskInvitationResponse> getPendingInvitations(User user, Pageable pageable) {
        return PagedResponse.fromPage(
                taskInvitationRepository.findByReceiverIdAndStatus(user.getId(), "pending", pageable)
                        .map(TaskInvitationResponse::fromTaskInvitation)
        );
    }

    @Transactional(readOnly = true)
    public List<TaskInvitationResponse> getInvitationsByTask(Long taskId) {
        return taskInvitationRepository.findByTaskId(taskId).stream()
                .map(TaskInvitationResponse::fromTaskInvitation)
                .collect(Collectors.toList());
    }
}
