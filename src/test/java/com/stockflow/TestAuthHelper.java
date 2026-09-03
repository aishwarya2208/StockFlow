package com.stockflow;

import com.stockflow.security.JwtService;
import com.stockflow.user.Role;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestAuthHelper {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TestAuthHelper(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String createAdminToken(String username) {
        User admin = userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(new User(
                        username,
                        username + "@stockflow.test",
                        passwordEncoder.encode("AdminPass@123"),
                        Role.ROLE_ADMIN,
                        "Admin",
                        "Test"
                ))
        );
        return jwtService.generateToken(admin);
    }

    public String createStaffToken(String username) {
        User staff = userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(new User(
                        username,
                        username + "@stockflow.test",
                        passwordEncoder.encode("StaffPass@123"),
                        Role.ROLE_STAFF,
                        "Staff",
                        "Test"
                ))
        );
        return jwtService.generateToken(staff);
    }
}
