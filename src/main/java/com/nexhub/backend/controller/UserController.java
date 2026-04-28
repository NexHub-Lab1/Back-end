package com.nexhub.backend.controller;


import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.UserDetailsResponse;
import com.nexhub.backend.dto.auth.AuthUserResponse;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService service;

    //esto es despues de nexhub.com ....
    @PostMapping("/{id}")
    public ApiResponse<AuthUserResponse> getUserById(Long id) {
        try {
            return new ApiResponse<>("success", "User found",
                    AuthUserResponse.fromUser(service.getUserById(id))
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/details/{id}")
    public ApiResponse<UserDetailsResponse> getUserDetailsById(@PathVariable Long id) {
        try {
            return new ApiResponse<>(
                    "success",
                    "User found",
                    UserDetailsResponse.fromUser(service.getUserById(id))
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
