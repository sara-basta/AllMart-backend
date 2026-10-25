package com.sara.allmart.dto.request;

import com.sara.allmart.entity.Role;

public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        Role role
) {
}
