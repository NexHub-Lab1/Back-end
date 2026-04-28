package com.nexhub.backend.controller;

import com.nexhub.backend.model.User;
import com.nexhub.backend.service.AuthService;
import com.nexhub.backend.service.GithubService;
import com.nexhub.backend.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Date;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtUtils jwt;
    @Mock
    private GithubService githubService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, jwt, githubService)).build();
    }

    @Nested
    @DisplayName("POST /api/auth/signup")
    class SignupEndpointTests {

        @Test
        void returnsCreatedResponseWithSafeUserPayload() throws Exception {
            User savedUser = sampleUser();
            when(authService.signup("manu", "manu@nexhub.dev", "securepass")).thenReturn(savedUser);

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "manu",
                                      "email": "manu@nexhub.dev",
                                      "password": "securepass"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Usuario creado correctamente"))
                    .andExpect(jsonPath("$.data.id").value(7))
                    .andExpect(jsonPath("$.data.username").value("manu"))
                    .andExpect(jsonPath("$.data.email").value("manu@nexhub.dev"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginEndpointTests {

        @Test
        void returnsOkWhenCredentialsAreValid() throws Exception {
            when(authService.login("manu@nexhub.dev", "securepass")).thenReturn(sampleUser());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "manu@nexhub.dev",
                                      "password": "securepass"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Login exitoso"))
                    .andExpect(jsonPath("$.data.user.username").value("manu"));
        }

        @Test
        void returnsUnauthorizedWhenCredentialsAreInvalid() throws Exception {
            when(authService.login("manu@nexhub.dev", "wrongpass"))
                    .thenThrow(new IllegalArgumentException("Password incorrecta"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "manu@nexhub.dev",
                                      "password": "wrongpass"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Password incorrecta"))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/updateaccount")
    class UpdateEndpointTests {

        @Test
        void returnsUpdatedUserPayload() throws Exception {
            User updatedUser = sampleUser();
            updatedUser.setUsername("manuel");
            updatedUser.setEmail("manuel@nexhub.dev");

            when(authService.updateAccount(
                    "manu@nexhub.dev",
                    "securepass",
                    "manuel",
                    "manuel@nexhub.dev",
                    "newsecurepass"
            )).thenReturn(updatedUser);
            when(jwt.generateToken("manuel@nexhub.dev")).thenReturn("updated-token");

            mockMvc.perform(post("/api/auth/updateaccount")
                            .principal(new TestingAuthenticationToken("manu@nexhub.dev", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "currentEmail": "manu@nexhub.dev",
                                      "currentPassword": "securepass",
                                      "newUsername": "manuel",
                                      "newEmail": "manuel@nexhub.dev",
                                      "newPassword": "newsecurepass"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Cuenta actualizada correctamente"))
                    .andExpect(jsonPath("$.data.user.username").value("manuel"))
                    .andExpect(jsonPath("$.data.user.email").value("manuel@nexhub.dev"))
                    .andExpect(jsonPath("$.data.token").value("updated-token"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/deleteaccount")
    class DeleteEndpointTests {

        @Test
        void returnsConfirmationMessage() throws Exception {
            when(authService.deleteAccount("manu@nexhub.dev", "securepass"))
                    .thenReturn("Cuenta eliminada correctamente");

            mockMvc.perform(post("/api/auth/deleteaccount")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "manu@nexhub.dev",
                                      "password": "securepass"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Cuenta eliminada correctamente"))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    private static User sampleUser() {
        User user = new User();
        setId(user, 7L);
        user.setUsername("manu");
        user.setEmail("manu@nexhub.dev");
        user.setPassword("hashed-password");
        user.setCreated_at(Date.valueOf("2026-03-01"));
        return user;
    }

    private static void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("No se pudo setear el id del usuario para el test", e);
        }
    }
}
