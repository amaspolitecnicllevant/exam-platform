package com.examplatform.dto;

import com.examplatform.domain.model.ProfessorDepartament;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProfessorDepartamentDto(UUID professorId, String professorNom, String professorEmail,
                                      UUID departamentId, String departamentNom, boolean esCap) {

    public static ProfessorDepartamentDto from(ProfessorDepartament pd) {
        return new ProfessorDepartamentDto(
                pd.getProfessor().getId(),
                pd.getProfessor().getName(),
                pd.getProfessor().getEmail(),
                pd.getDepartament().getId(),
                pd.getDepartament().getNom(),
                pd.isEsCap());
    }

    public record AddRequest(@NotNull UUID professorId, boolean esCap) {}
}
