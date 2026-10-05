package com.examplatform.dto;

import com.examplatform.domain.model.Departament;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepartamentDto(UUID id, String nom, LocalDateTime createdAt) {

    public static DepartamentDto from(Departament d) {
        return new DepartamentDto(d.getId(), d.getNom(), d.getCreatedAt());
    }

    public record CreateRequest(@NotBlank String nom) {}
}
