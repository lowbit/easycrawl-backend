package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.LoginRequest;
import com.rijads.easycrawl.dto.RegistrationRequest;
import com.rijads.easycrawl.dto.UserResponse;
import com.rijads.easycrawl.model.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(
            final CustomUserDetailsService customUserDetailsService,
            final PasswordEncoder passwordEncoder,
            final AuthenticationManager authenticationManager,
            final JwtEncoder jwtEncoder,
            final TokenService tokenService) {
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public ResponseEntity<UserResponse> registerUser(
            final RegistrationRequest registrationRequest) {

        if (customUserDetailsService.findByUsername(registrationRequest.getUsername()) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User with the same username already exists!");
        }
        if (customUserDetailsService.findByEmail(registrationRequest.getEmail()) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User with the same email already exists!");
        }

        User user = new User();
        user.setUsername(registrationRequest.getUsername());
        user.setEmail(registrationRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registrationRequest.getPassword()));
        user.setFirstName(registrationRequest.getFirstName());
        user.setLastName(registrationRequest.getLastName());
        user.setAuthorities(Set.of("ROLE_USER", "ROLE_ADMIN"));
        user.setEnabled(true);
        user.setCreated(LocalDateTime.now());
        user.setCreatedBy("System");
        user = customUserDetailsService.saveUser(user);
        String jwt = tokenService.generateToken(user);
        UserResponse response = mapUserResponse(jwt, user);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<UserResponse> login(final LoginRequest loginRequest) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    loginRequest.getUsername(), loginRequest.getPassword()));
            User user = customUserDetailsService.findByUsername(authentication.getName());
            String jwt = tokenService.generateToken(user);
            UserResponse response = mapUserResponse(jwt, user);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private UserResponse mapUserResponse(final String jwt, final User user) {
        UserResponse response = new UserResponse();
        response.setToken(jwt);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setAuthorities(user.getAuthorities());
        return response;
    }
}
