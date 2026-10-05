package com.examplatform.dto;

import com.examplatform.domain.model.Matricula;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatriculaDto(UUID id, UUID alumneId, String alumneNom,
                           UUID modulId, String modulNom, String modulCodi, String curs) {

    public static MatriculaDto from(Matricula m) {
        return new MatriculaDto(m.getId(),
                m.getAlumne().getId(), m.getAlumne().getName(),
                m.getModul().getId(), m.getModul().getNom(), m.getModul().getCodi(),
                m.getCurs());
    }

    /** Matrícula de molts alumnes alhora al mateix mòdul i curs. */
    public record LotRequest(
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 1000) java.util.List<UUID> alumneIds,
            @NotNull UUID modulId,
            @NotBlank @jakarta.validation.constraints.Size(max = 20) String curs) {}

    /** Resultat d'una matrícula en bloc: les noves i quants ja hi eren. */
    public record LotResultat(java.util.List<MatriculaDto> matriculades, int jaMatriculats) {}

    public record CreateRequest(
            @NotNull UUID alumneId,
            @NotNull UUID modulId,
            @NotBlank String curs) {}
}
