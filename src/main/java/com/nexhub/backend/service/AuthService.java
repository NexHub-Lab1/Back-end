package com.nexhub.backend.service;

import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.checker.UserChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Date;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User signup(String username, String email, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        if (!UserChecker.isValid(user)) {
            throw new IllegalArgumentException("Los datos del usuario no son validos");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El username ya esta en uso");
        }

        Date now = new Date(System.currentTimeMillis());
        user.setCreated_at(now);
        user.setUpdated_at(now);
        user.setLast_active_at(now);
        user.setTotal_points(0);
        user.setStreak_day(0);
        user.setReputation_score(0);

        return userRepository.save(user);
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

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

        return "Usuario verificado. Ya se puede resetear la password";
    }

    public String resetPassword(String email, String newPassword) {
        validateEmail(email);
        validatePassword(newPassword);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdated_at(new Date(System.currentTimeMillis()));

        userRepository.save(user);
        return "Password actualizada correctamente";
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
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
}
