package com.examplatform.dto;

import com.examplatform.domain.model.Imparticio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ImparticioDto(UUID id, UUID professorId, String professorNom,
                            UUID modulId, String modulCodi, String curs) {

    public static ImparticioDto from(Imparticio i) {
        return new ImparticioDto(i.getId(),
                i.getProfessor().getId(), i.getProfessor().getName(),
                i.getModul().getId(), i.getModul().getCodi(),
                i.getCurs());
    }

    public record CreateRequest(
            @NotNull UUID professorId,
            @NotBlank String curs) {}
}
