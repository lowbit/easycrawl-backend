package com.rijads.easycrawl.dto;

import java.util.Set;

public class UserResponse {
    private String username;
    private String email;
    private String token;
    private Set<String> authorities;

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(final Set<String> authorities) {
        this.authorities = authorities;
    }
}
