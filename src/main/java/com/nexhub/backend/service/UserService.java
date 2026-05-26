package com.nexhub.backend.service;

import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.checker.UserChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado por email: " + email));
    }

    public User addPoints(Long userId, Integer points) {
        User user = getUserById(userId);
        user.setTotal_points(user.getTotal_points() + points);
        return userRepository.save(user);
    }

    public User saveUser(User user) {
        if (UserChecker.isValid(user))
            return userRepository.save(user);
        return user;
    }

}