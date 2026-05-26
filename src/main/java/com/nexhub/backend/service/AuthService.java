package com.nexhub.backend.service;

import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.checker.UserChecker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "active";
    private static final String DEACTIVATED_STATUS = "deactivated";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProjectRepository projectRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TagRepository tagRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ProjectRepository projectRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskSubmissionRepository taskSubmissionRepository,
            TagRepository tagRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.projectRepository = projectRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.tagRepository = tagRepository;
    }

    public User signup(String username, String email, String password) {
        User userToValidate = new User();
        userToValidate.setUsername(username);
        userToValidate.setEmail(email);
        userToValidate.setPassword(password);

        if (!UserChecker.isValid(userToValidate)) {
            throw new IllegalArgumentException("Los datos del usuario no son validos");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El username ya esta en uso");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        Date now = new Date(System.currentTimeMillis());
        user.setCreated_at(now);
        user.setUpdated_at(now);
        user.setLast_active_at(now);
        user.setStatus(ACTIVE_STATUS);
        user.setTotal_points(0);
        user.setStreak_day(0);
        user.setReputation_score(0);
        user.setFollows(new HashSet<>());
        user.setSkills(new HashSet<>());

        return userRepository.save(user);
    }

    public User login(String emailOrUsername, String password) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            throw new IllegalArgumentException("El email o username es obligatorio");
        }

        String identifier = emailOrUsername.trim();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (isDeactivated(user)) {
            throw new IllegalArgumentException("La cuenta esta desactivada");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Password incorrecta");
        }

        user.setLast_active_at(new Date(System.currentTimeMillis()));
        return userRepository.save(user);
    }

    public String signout() {
        return "Signout realizado correctamente";
    }

    public String forgotPassword(String email) {
        validateEmail(email);

        userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return "Usuario verificado ,ya se puede resetear la password";
    }

    public String resetPassword(String email, String newPassword) {
        validateEmail(email);
        validatePassword(newPassword);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (isGithubUser(user)) {
            throw new IllegalArgumentException("Las cuentas vinculadas con GitHub no pueden cambiar password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdated_at(new Date(System.currentTimeMillis()));

        userRepository.save(user);
        return "Password actualizada correctamente";
    }

    public String deleteAccount(String email, String password) {
        validateEmail(email);
        validatePassword(password);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Password incorrecta");
        }

        if (hasHistoricalActivity(user.getId())) {
            user.setStatus(DEACTIVATED_STATUS);
            user.setUpdated_at(new Date(System.currentTimeMillis()));
            userRepository.save(user);
            return "Cuenta desactivada correctamente";
        }

        userRepository.delete(user);
        return "Cuenta eliminada correctamente";
    }

    public User updateAccount(
            String authenticatedEmail,
            String currentPassword,
            String newUsername,
            String newEmail,
            String newPassword,
            Set<String> skills
    ) {
        validateEmail(authenticatedEmail);

        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        boolean githubUser = isGithubUser(user);

        if (githubUser) {
            if (newPassword != null && !newPassword.isBlank()) {
                throw new IllegalArgumentException("Las cuentas vinculadas con GitHub no pueden cambiar password");
            }
        } else {
            validatePassword(currentPassword);
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new IllegalArgumentException("Password incorrecta");
            }
        }

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsername(newUsername)) {
                throw new IllegalArgumentException("El username ya esta en uso");
            }
            user.setUsername(newUsername.trim());
        }

        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(user.getEmail())) {
            validateEmail(newEmail);
            if (userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("El email ya esta registrado");
            }
            user.setEmail(newEmail.trim());
        }

        if (!githubUser && newPassword != null && !newPassword.isBlank()) {
            validatePassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (skills != null) {
            user.setSkills(resolveTags(skills));
        }

        if (!UserChecker.usernameCheck(user) || !UserChecker.emailCheck(user)) {
            throw new IllegalArgumentException("Los datos actualizados no son validos");
        }

        user.setUpdated_at(new Date(System.currentTimeMillis()));
        return userRepository.save(user);
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorioo");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password es obligatoria");
        }

        User user = new User();
        user.setPassword(password);
        if (!UserChecker.passwordSecurityCheck(user)) {
            throw new IllegalArgumentException("La password debe tener al menos 8 caracteres");
        }
    }

    private boolean hasHistoricalActivity(Long userId) {
        if (userId == null) {
            return false;
        }

        return projectRepository.existsByOwner_Id(userId)
                || projectRepository.existsByContributors_Id(userId)
                || taskAssignmentRepository.existsByUser_Id(userId)
                || taskSubmissionRepository.existsByUser_Id(userId)
                || taskSubmissionRepository.existsByReviewer_Id(userId);
    }

    private boolean isDeactivated(User user) {
        return user.getStatus() != null && DEACTIVATED_STATUS.equalsIgnoreCase(user.getStatus());
    }

    private boolean isGithubUser(User user) {
        return user.getGithub_id() != null
                || (user.getGithub_username() != null && !user.getGithub_username().isBlank());
    }

    private Set<Tag> resolveTags(Set<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Tag> resolvedTags = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag == null) {
                continue;
            }

            String normalizedTag = rawTag.trim();
            if (normalizedTag.isEmpty()) {
                continue;
            }

            Tag tag = tagRepository.findByNameIgnoreCase(normalizedTag)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(normalizedTag);
                        return tagRepository.save(newTag);
                    });
            resolvedTags.add(tag);
        }

        return resolvedTags;
    }
}
