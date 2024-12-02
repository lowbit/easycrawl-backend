package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.LoginRequest;
import com.rijads.easycrawl.dto.RegistrationRequest;
import com.rijads.easycrawl.dto.UserResponse;
import com.rijads.easycrawl.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(
            @Valid @RequestBody RegistrationRequest registrationRequest) {
        return authService.registerUser(registrationRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
}
