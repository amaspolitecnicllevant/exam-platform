package com.examplatform.dto;

import com.examplatform.domain.model.Answer;

import java.math.BigDecimal;
import java.util.UUID;

public record AnswerDto(
        UUID id,
        UUID questionId,
        String contingut,
        String executionOutput,
        BigDecimal autoScore,
        BigDecimal manualScore,
        String autoFeedback,
        String comentari,
        /** Fitxer pujat (preguntes de lliurament): nom original i mida en bytes. */
        String fitxerNom,
        Long fitxerMida
) {
    public static AnswerDto from(Answer a) {
        return new AnswerDto(a.getId(), a.getQuestion().getId(), a.getContingut(),
                a.getExecutionOutput(), a.getAutoScore(), a.getManualScore(), a.getAutoFeedback(),
                a.getComentari(), a.getFitxerNom(), a.getFitxerMida());
    }

    /** Per a l'alumne abans que es publiquin les notes: sense puntuacions ni motius. */
    public static AnswerDto senseNotes(Answer a) {
        return new AnswerDto(a.getId(), a.getQuestion().getId(), a.getContingut(),
                a.getExecutionOutput(), null, null, null, null, a.getFitxerNom(), a.getFitxerMida());
    }

    public record SaveRequest(UUID questionId, String contingut) {}

    public record ScoreRequest(BigDecimal manualScore) {}

    public record ComentariRequest(String comentari) {}
}
