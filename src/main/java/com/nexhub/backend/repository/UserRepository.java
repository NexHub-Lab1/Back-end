package com.nexhub.backend.repository;

import com.nexhub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("select u from User u where u.github_id = :githubId")
    Optional<User> findByGithubId(@Param("githubId") Integer githubId);

    @Query("select u from User u where u.github_username = :githubUsername")
    Optional<User> findByGithubUsername(@Param("githubUsername") String githubUsername);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
