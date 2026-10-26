package com.examplatform.dto;

import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Progrés d'un alumne: per mòdul i competència, els exàmens on l'ha suspesa i les preguntes on ha perdut punts.
 * Només hi surten exàmens entregats amb les notes publicades.
 */
public record ProgresDto(List<Modul> moduls) {

    public record Modul(UUID modulId, String modulNom, List<Competencia> competencies) {}

    /**
     * @param notaGlobal nota sobre 10 de la competència sumant tots els exàmens publicats del mòdul que la treballen
     * @param suspesos   exàmens on la competència està suspesa, en ordre cronològic
     */
    public record Competencia(String codi, BigDecimal notaGlobal, List<Examen> suspesos) {}

    public record Examen(UUID sessionId, UUID examId, String titol, Instant data, BigDecimal nota,
                         List<Pregunta> preguntes) {}

    public record Pregunta(int numero, QuestionType tipus, String enunciat, BigDecimal punts, BigDecimal obtinguts) {}
}
