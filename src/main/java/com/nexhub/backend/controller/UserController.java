package com.nexhub.backend.controller;


import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.auth.AuthUserResponse;
import com.nexhub.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ApiResponse<AuthUserResponse> getUserById(Long id) {
        return new ApiResponse<>("success", "User found", AuthUserResponse.fromUser(service.getUserById(id)));
    }
}
