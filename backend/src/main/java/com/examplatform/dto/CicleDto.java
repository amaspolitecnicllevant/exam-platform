package com.examplatform.dto;

import com.examplatform.domain.model.Cicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CicleDto(UUID id, String codi, String nom, UUID departamentId, String departamentNom) {

    public static CicleDto from(Cicle c) {
        return new CicleDto(c.getId(), c.getCodi(), c.getNom(),
                c.getDepartament().getId(), c.getDepartament().getNom());
    }

    public record CreateRequest(
            @NotBlank String codi,
            @NotBlank String nom,
            @NotNull UUID departamentId) {}
}
