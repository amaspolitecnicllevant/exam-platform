package com.examplatform.dto;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserDto(UUID id, String name, String email, Role role) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }

    public record CreateRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 255) String name,
            @Email @NotBlank @jakarta.validation.constraints.Size(max = 255) String email,
            @NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72) String password,
            @NotNull Role role
    ) {}
}
