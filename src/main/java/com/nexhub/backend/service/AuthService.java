package com.nexhub.backend.service;

import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.checker.UserChecker;
import org.springframework.stereotype.Service;

import java.sql.Date;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User signup(String username, String email, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);

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

        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("Password incorrecta");
        }

        user.setLast_active_at(new Date(System.currentTimeMillis()));
        return userRepository.save(user);
    }
}
