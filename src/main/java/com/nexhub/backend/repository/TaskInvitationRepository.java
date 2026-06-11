package com.nexhub.backend.repository;

import com.nexhub.backend.model.TaskInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskInvitationRepository extends JpaRepository<TaskInvitation, Long> {

    @EntityGraph(attributePaths = {"task", "task.project", "sender", "receiver"})
    @Query("select ti from TaskInvitation ti where ti.receiver.id = :receiverId and lower(ti.status) = :status")
    Page<TaskInvitation> findByReceiverIdAndStatus(
            @Param("receiverId") Long receiverId,
            @Param("status") String status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"task", "task.project", "sender", "receiver"})
    @Query("select ti from TaskInvitation ti where ti.task.id = :taskId")
    List<TaskInvitation> findByTaskId(@Param("taskId") Long taskId);

    @Query("select count(ti) > 0 from TaskInvitation ti where ti.task.id = :taskId and ti.receiver.id = :receiverId and lower(ti.status) = :status")
    boolean existsByTaskIdAndReceiverIdAndStatus(
            @Param("taskId") Long taskId,
            @Param("receiverId") Long receiverId,
            @Param("status") String status
    );
}
