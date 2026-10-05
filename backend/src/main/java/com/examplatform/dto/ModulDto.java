package com.examplatform.dto;

import com.examplatform.domain.model.Modul;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ModulDto(UUID id, String codi, String nom,
                       UUID cicleId, String cicleNom, String departamentNom) {

    public static ModulDto from(Modul m) {
        return new ModulDto(m.getId(), m.getCodi(), m.getNom(),
                m.getCicle().getId(), m.getCicle().getNom(),
                m.getCicle().getDepartament().getNom());
    }

    public record CreateRequest(
            @NotBlank String codi,
            @NotBlank String nom,
            @NotNull UUID cicleId) {}
}
