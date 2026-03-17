package com.nexhub.backend.controller;


import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService service;

    //esto es despues de nexhub.com ....
    @PostMapping("/{id}")
    public ApiResponse<User> getUserById(Long id) {
        return new ApiResponse<>("success", "User found", service.getUserById(id));
    }
}
