package com.nexhub.backend.utils.checker;

import com.nexhub.backend.model.User;
import java.util.regex.Pattern;

public class UserChecker {
    // Expresión regular básica para validar emails
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";


    /**
     * Validates user email against regex pattern
     */
    public static boolean emailCheck(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }

        return Pattern.compile(EMAIL_PATTERN)
                .matcher(user.getEmail())
                .matches();
    }

    /**
     * Validates username length and no-space rule
     */
    public static boolean usernameCheck(User user) {
        if (user.getUsername() == null) return false;
        String username = user.getUsername().trim();
        return username.length() >= 3 && username.length() <= 20 && !username.contains(" ");
    }

    public static boolean passwordSecurityCheck(User user) {
        return user.getPassword() != null && user.getPassword().length() >= 8;
    }

    public static boolean isRequiredDataPresent(User user) {
        return user.getUsername() != null &&
                user.getEmail() != null &&
                user.getPassword() != null;
    }

    public static boolean isValid(User user) {
        return isRequiredDataPresent(user) &&
                emailCheck(user) &&
                usernameCheck(user) &&
                passwordSecurityCheck(user);
    }
}