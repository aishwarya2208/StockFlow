package com.stockflow.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.user.dto.LoginRequest;
import com.stockflow.user.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/auth/register: successfully registers a new user")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "testuser",
                "testuser@stockflow.internal",
                "Secret@123",
                Role.ROLE_STAFF,
                "Test",
                "User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("testuser")))
                .andExpect(jsonPath("$.data.email", is("testuser@stockflow.internal")))
                .andExpect(jsonPath("$.data.role", is("ROLE_STAFF")))
                .andExpect(jsonPath("$.data.token", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register: rejects duplicate username with HTTP 409")
    void register_duplicateUsername() throws Exception {
        RegisterRequest request1 = new RegisterRequest(
                "duplicate_user",
                "dup1@stockflow.internal",
                "Secret@123",
                Role.ROLE_STAFF,
                "User",
                "One"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        RegisterRequest request2 = new RegisterRequest(
                "duplicate_user",
                "dup2@stockflow.internal",
                "Secret@123",
                Role.ROLE_STAFF,
                "User",
                "Two"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: successfully authenticates and returns valid JWT")
    void login_success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "loginuser",
                "loginuser@stockflow.internal",
                "CorrectPassword@123",
                Role.ROLE_STAFF,
                "Login",
                "User"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("loginuser", "CorrectPassword@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.username", is("loginuser")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: rejects bad password with HTTP 401")
    void login_badPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "user_bad_pass",
                "badpass@stockflow.internal",
                "ValidPassword@123",
                Role.ROLE_STAFF,
                "User",
                "Pass"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("user_bad_pass", "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me: returns authenticated user profile with Bearer token")
    void me_authenticated() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "profileuser",
                "profile@stockflow.internal",
                "Profile@123",
                Role.ROLE_STAFF,
                "Profile",
                "Owner"
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("profileuser")))
                .andExpect(jsonPath("$.data.email", is("profile@stockflow.internal")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me: rejects unauthenticated requests with HTTP 401")
    void me_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
