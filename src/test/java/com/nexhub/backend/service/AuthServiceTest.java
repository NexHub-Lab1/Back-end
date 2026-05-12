package com.nexhub.backend.service;

import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup")
    class SignupTests {

        @Test
        void createsUserWithHashedPasswordAndDefaultStats() {
            when(userRepository.existsByEmail("manu@nexhub.dev")).thenReturn(false);
            when(userRepository.existsByUsername("manu")).thenReturn(false);
            when(passwordEncoder.encode("securepass")).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                setId(savedUser, 42L);
                return savedUser;
            });

            User createdUser = authService.signup("manu", "manu@nexhub.dev", "securepass");

            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            User savedUser = savedUserCaptor.getValue();

            assertThat(createdUser.getId()).isEqualTo(42L);
            assertThat(savedUser.getUsername()).isEqualTo("manu");
            assertThat(savedUser.getEmail()).isEqualTo("manu@nexhub.dev");
            assertThat(savedUser.getPassword()).isEqualTo("hashed-password");
            assertThat(savedUser.getTotal_points()).isZero();
            assertThat(savedUser.getStreak_day()).isZero();
            assertThat(savedUser.getReputation_score()).isZero();
            assertThat(savedUser.getStatus()).isEqualTo("active");
            assertThat(savedUser.getCreated_at()).isNotNull();
            assertThat(savedUser.getUpdated_at()).isNotNull();
            assertThat(savedUser.getLast_active_at()).isNotNull();
        }

        @Test
        void rejectsShortPasswordsBeforeEncoding() {
            assertThatThrownBy(() -> authService.signup("manu", "manu@nexhub.dev", "short"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Los datos del usuario no son validos");

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail("manu@nexhub.dev")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup("manu", "manu@nexhub.dev", "securepass"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El email ya esta registrado");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        void updatesLastActiveAtWhenCredentialsAreValid() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            Date previousLastActive = Date.valueOf("2026-03-01");
            existingUser.setLast_active_at(previousLastActive);

            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("securepass", "hashed-password")).thenReturn(true);
            when(userRepository.save(existingUser)).thenReturn(existingUser);

            User loggedInUser = authService.login("manu@nexhub.dev", "securepass");

            assertThat(loggedInUser).isSameAs(existingUser);
            assertThat(existingUser.getLast_active_at()).isNotNull();
            assertThat(existingUser.getLast_active_at()).isAfterOrEqualTo(previousLastActive);
            verify(userRepository).save(existingUser);
        }

        @Test
        void rejectsWrongPassword() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongpass", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login("manu@nexhub.dev", "wrongpass"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password incorrecta");

            verify(userRepository, never()).save(any());
        }

        @Test
        void rejectsDeactivatedUsers() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            existingUser.setStatus("deactivated");
            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.login("manu@nexhub.dev", "securepass"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La cuenta esta desactivada");

            verify(passwordEncoder, never()).matches(any(), any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateAccount")
    class UpdateAccountTests {

        @Test
        void updatesProvidedFieldsAndHashesNewPassword() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            existingUser.setUpdated_at(Date.valueOf("2026-03-01"));

            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("securepass", "hashed-password")).thenReturn(true);
            when(userRepository.existsByUsername("manuel")).thenReturn(false);
            when(userRepository.existsByEmail("manuel@nexhub.dev")).thenReturn(false);
            when(passwordEncoder.encode("newsecurepass")).thenReturn("new-hash");
            when(userRepository.save(existingUser)).thenReturn(existingUser);

            User updatedUser = authService.updateAccount(
                    "manu@nexhub.dev",
                    "securepass",
                    "manuel",
                    "manuel@nexhub.dev",
                    "newsecurepass",
                    null
            );

            assertThat(updatedUser.getUsername()).isEqualTo("manuel");
            assertThat(updatedUser.getEmail()).isEqualTo("manuel@nexhub.dev");
            assertThat(updatedUser.getPassword()).isEqualTo("new-hash");
            assertThat(updatedUser.getUpdated_at()).isAfterOrEqualTo(Date.valueOf("2026-03-01"));
            verify(userRepository).save(existingUser);
        }

        @Test
        void updatesUserSkillsUsingExistingAndNewTags() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            Tag existingTag = new Tag();
            existingTag.setName("React");

            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("securepass", "hashed-password")).thenReturn(true);
            when(tagRepository.findByNameIgnoreCase("React")).thenReturn(Optional.of(existingTag));
            when(tagRepository.findByNameIgnoreCase("TypeScript")).thenReturn(Optional.empty());
            when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userRepository.save(existingUser)).thenReturn(existingUser);

            User updatedUser = authService.updateAccount(
                    "manu@nexhub.dev",
                    "securepass",
                    "manu",
                    "manu@nexhub.dev",
                    "",
                    Set.of("React", "TypeScript")
            );

            assertThat(updatedUser.getSkills())
                    .extracting(Tag::getName)
                    .containsExactlyInAnyOrder("React", "TypeScript");
            verify(userRepository).save(existingUser);
        }
    }

    @Nested
    @DisplayName("deleteAccount")
    class DeleteAccountTests {

        @Test
        void deletesUserWhenCredentialsAreValid() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("securepass", "hashed-password")).thenReturn(true);

            String result = authService.deleteAccount("manu@nexhub.dev", "securepass");

            assertThat(result).isEqualTo("Cuenta eliminada correctamente");
            verify(userRepository).delete(existingUser);
        }

        @Test
        void deactivatesUserWhenTheyHaveHistoricalActivity() {
            User existingUser = userWithCredentials("manu", "manu@nexhub.dev", "hashed-password");
            setId(existingUser, 7L);
            when(userRepository.findByEmail("manu@nexhub.dev")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("securepass", "hashed-password")).thenReturn(true);
            when(projectRepository.existsByOwner_Id(7L)).thenReturn(true);
            when(userRepository.save(existingUser)).thenReturn(existingUser);

            String result = authService.deleteAccount("manu@nexhub.dev", "securepass");

            assertThat(result).isEqualTo("Cuenta desactivada correctamente");
            assertThat(existingUser.getStatus()).isEqualTo("deactivated");
            verify(userRepository).save(existingUser);
            verify(userRepository, never()).delete(existingUser);
        }
    }

    private static User userWithCredentials(String username, String email, String passwordHash) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordHash);
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
