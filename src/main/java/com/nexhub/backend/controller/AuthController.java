package com.nexhub.backend.controller;

import com.nexhub.backend.service.AuthService;
import com.nexhub.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;
    private AuthService authService;

    @PostMapping("/login")
    public String login(@RequestBody String username, @RequestBody String password) {
        return "working";
    }

    @PostMapping("/signup")
    public String signup(@RequestBody String username, @RequestBody String password) {
        return "working";
    }

    @PostMapping("/signout")
    public String signout() {
        return "working";
    }

    @PostMapping("/fortgotpassword")
    public String forgotpassword() {
        return "working";
    }

    @PostMapping("/resetpassword")
    public String resetpassword() {
        return "working";
    }

    @PostMapping("/github")
    public String github() {
        return "working";
    }

}
