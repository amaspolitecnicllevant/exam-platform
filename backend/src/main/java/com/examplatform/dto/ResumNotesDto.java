package com.examplatform.dto;

import com.examplatform.domain.model.ExamStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Aprovats i mitjana de cada examen del professor i de cada UT (entre els alumnes que han entregat). */
public record ResumNotesDto(List<Examen> examens, List<Unitat> unitats) {

    public record Examen(
            UUID id,
            String titol,
            /** Unitat de treball de l'examen; null si no n'hi ha. */
            String ut,
            String modulNom,
            ExamStatus estat,
            /** Alumnes amb l'examen entregat (els que compten). */
            int entregats,
            /** Entregats amb nota >= 5. */
            int aprovats,
            /** % d'aprovats sobre els entregats (0–100); null si no n'hi ha cap. */
            BigDecimal percentAprovats,
            /** Nota mitjana sobre 10; null si no n'hi ha cap. */
            BigDecimal mitjana
    ) {}

    /** Totes les notes dels exàmens d'una mateixa UT (i mòdul) ajuntades. */
    public record Unitat(
            String ut,
            String modulNom,
            int examens,
            int entregats,
            int aprovats,
            BigDecimal percentAprovats,
            BigDecimal mitjana
    ) {}
}
