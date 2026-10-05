package com.examplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Alumnes que haurien de fer la recuperació d'un examen: suspesos i no presentats. */
public record RecuperacioDto(
        /** Respostes encara sense nota: les notes són provisionals (compten 0). */
        int respostesPendents,
        List<Candidat> candidats
) {
    public enum Motiu { SUSPES, NO_PRESENTAT }

    public record Candidat(
            UUID alumneId,
            String nom,
            String email,
            Motiu motiu,
            /** Nota sobre 10 (null si no ha entregat). */
            BigDecimal nota,
            /** Té respostes sense nota: la nota pot pujar en acabar de corregir. */
            boolean notaProvisional
    ) {}

    public record CrearGrupRequest(@NotBlank String nom, @NotEmpty List<UUID> alumneIds) {}
}
