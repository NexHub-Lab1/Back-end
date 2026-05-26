package com.nexhub.backend.controller;


import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.UserDetailsResponse;
import com.nexhub.backend.dto.auth.AuthUserResponse;
import com.nexhub.backend.dto.follow.FollowRequest;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.NotificationService;
import com.nexhub.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService service;

    @Autowired
    private NotificationService notificationService;

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

    @GetMapping("/all_users_details")
    public ApiResponse<List<UserDetailsResponse>> getTopDevs() {
        try{
            List<UserDetailsResponse> users = new LinkedList<>();
            System.out.println(service.getAllUsers());
            for(User user : service.getAllUsers()) {
                users.add(UserDetailsResponse.fromUser(user));
            }
            return new ApiResponse<>(
                    "success",
                    "top users (all users)",
                    users
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/follow")
    public ApiResponse<UserDetailsResponse> follow(@RequestBody FollowRequest request) {
        try {
            final User current = service.getUserById(request.from());
            final User to_follow = service.getUserById(request.to());
            current.getFollows().add(to_follow);
            service.saveUser(current);

            notificationService.sendNotification(
                    to_follow,
                    current.getUsername() + " started following you!",
                    "INFO",
                    "/user/" + current.getId()
            );

            return new ApiResponse<>(
                    "success",
                    current.getId() + " follow " + to_follow.getId(),
                   UserDetailsResponse.fromUser(to_follow)
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/unfollow")
    public ApiResponse<UserDetailsResponse> unfollow(@RequestBody FollowRequest request) {
        try {
            final User current = service.getUserById(request.from());
            final User to_unfollow = service.getUserById(request.to());
            current.getFollows().remove(to_unfollow);
            service.saveUser(current);
            return new ApiResponse<>(
                    "success",
                    current.getId() + " follow " + to_unfollow.getId(),
                    UserDetailsResponse.fromUser(to_unfollow)
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/followed/{id}")
    public ApiResponse<List<UserDetailsResponse>> getFollowedUsers(@PathVariable Long id) {
        try {
            final User current = service.getUserById(id);
            final List<UserDetailsResponse> response = current.getFollows()
                    .stream().map(UserDetailsResponse::fromUser)
                    .toList();
            return new ApiResponse<>(
                    "success",
                    "Users followed by " + current.getId(),
                    response
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/followers/{id}")
    public ApiResponse<List<UserDetailsResponse>> getFollowers(@PathVariable Long id) {
        try {
            final List<UserDetailsResponse> response = service.getFollowers(id)
                    .stream().map(UserDetailsResponse::fromUser)
                    .toList();
            return new ApiResponse<>(
                    "success",
                    "Users following " + id,
                    response
            );
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
