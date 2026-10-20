package com.examplatform.dto;

import com.examplatform.domain.model.UnitatTreball;

import java.util.List;
import java.util.UUID;

/** Programació d'un mòdul: les seves competències i les unitats de treball que les treballen. */
public record ProgramacioDto(UUID modulId, List<Competencia> competencies, List<Unitat> unitats) {

    public record Competencia(UUID id, String codi, String descripcio) {
        public static Competencia de(com.examplatform.domain.model.Competencia c) {
            return new Competencia(c.getId(), c.getCodi(), c.getDescripcio());
        }
    }

    /** {@code competencies} són els codis de les competències que treballa la unitat. */
    public record Unitat(UUID id, String codi, String nom, List<String> competencies, Integer pesExamens) {
        public static Unitat de(UnitatTreball u) {
            return new Unitat(u.getId(), u.getCodi(), u.getNom(),
                    u.getCompetencies().stream()
                            .sorted(java.util.Comparator.comparingInt(c -> c.getOrdre()))
                            .map(com.examplatform.domain.model.Competencia::getCodi).toList(),
                    u.getPesExamens());
        }
    }

    /** Cos de la substitució completa de la programació. */
    public record Peticio(List<CompetenciaPeticio> competencies, List<UnitatPeticio> unitats) {}

    public record CompetenciaPeticio(String codi, String descripcio) {}

    /** {@code pesExamens}: % de la nota de la unitat que ve dels exàmens (0–100; la resta, de pràctiques); null = sense definir. */
    public record UnitatPeticio(String codi, String nom, List<String> competencies, Integer pesExamens) {
        public UnitatPeticio(String codi, String nom, List<String> competencies) {
            this(codi, nom, competencies, null);
        }
    }

    /** Unitats que avalua un examen. */
    public record UnitatsExamen(List<UUID> unitatIds) {}

    /** Competències que declara un examen. */
    public record CompetenciesDeLExamen(List<String> codis) {}
}
