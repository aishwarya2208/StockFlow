package com.stockflow.user;

import com.stockflow.common.exception.DuplicateResourceException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.security.JwtService;
import com.stockflow.user.dto.AuthResponse;
import com.stockflow.user.dto.LoginRequest;
import com.stockflow.user.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User(
                "john_doe",
                "john@example.com",
                "encodedPassword123",
                Role.ROLE_STAFF,
                "John",
                "Doe"
        );
        sampleUser.setId(1L);
    }

    @Test
    @DisplayName("register: successfully registers new user and returns JWT token")
    void register_success() {
        RegisterRequest request = new RegisterRequest(
                "john_doe",
                "john@example.com",
                "Password@123",
                Role.ROLE_STAFF,
                "John",
                "Doe"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encodedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(sampleUser)).thenReturn("jwt-token-12345");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token-12345");
        assertThat(response.getUsername()).isEqualTo("john_doe");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_STAFF);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: throws DuplicateResourceException when username already exists")
    void register_duplicateUsername() {
        RegisterRequest request = new RegisterRequest(
                "john_doe",
                "new@example.com",
                "Password@123",
                Role.ROLE_STAFF,
                "John",
                "Doe"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("User already exists with username: 'john_doe'");
    }

    @Test
    @DisplayName("register: throws DuplicateResourceException when email already exists")
    void register_duplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "new_user",
                "john@example.com",
                "Password@123",
                Role.ROLE_STAFF,
                "John",
                "Doe"
        );

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("User already exists with email: 'john@example.com'");
    }

    @Test
    @DisplayName("login: successfully authenticates and returns JWT token")
    void login_success() {
        LoginRequest request = new LoginRequest("john_doe", "Password@123");

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("jwt-token-login");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token-login");
        assertThat(response.getUsername()).isEqualTo("john_doe");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login: throws BadCredentialsException when credentials invalid")
    void login_badCredentials() {
        LoginRequest request = new LoginRequest("john_doe", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Bad credentials");
    }
}
