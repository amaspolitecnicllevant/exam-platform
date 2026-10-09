package com.examplatform.dto;

import com.examplatform.domain.model.Answer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        Long fitxerMida,
        /** Revisió amb IA aplicada a aquesta resposta. NOMÉS per al professor: l'alumne sempre rep null. */
        RevisioIa revisioIa
) {
    public record RevisioIa(LocalDateTime el, BigDecimal notaAbans, String justificacio) {}

    public static AnswerDto from(Answer a) {
        return new AnswerDto(a.getId(), a.getQuestion().getId(), a.getContingut(),
                a.getExecutionOutput(), a.getAutoScore(), a.getManualScore(), a.getAutoFeedback(),
                a.getComentari(), a.getFitxerNom(), a.getFitxerMida(), null);
    }

    /** Per al professor: com {@link #from} i, si n'hi ha, la revisió amb IA aplicada. */
    public static AnswerDto fromProfessor(Answer a) {
        RevisioIa revisio = a.getRevisioIaEl() == null ? null
                : new RevisioIa(a.getRevisioIaEl(), a.getRevisioIaNotaAbans(), a.getRevisioIaJustificacio());
        return new AnswerDto(a.getId(), a.getQuestion().getId(), a.getContingut(),
                a.getExecutionOutput(), a.getAutoScore(), a.getManualScore(), a.getAutoFeedback(),
                a.getComentari(), a.getFitxerNom(), a.getFitxerMida(), revisio);
    }

    /** Per a l'alumne abans que es publiquin les notes: sense puntuacions ni motius. */
    public static AnswerDto senseNotes(Answer a) {
        return new AnswerDto(a.getId(), a.getQuestion().getId(), a.getContingut(),
                a.getExecutionOutput(), null, null, null, null, a.getFitxerNom(), a.getFitxerMida(), null);
    }

    public record SaveRequest(UUID questionId, String contingut) {}

    public record ScoreRequest(BigDecimal manualScore) {}

    public record ComentariRequest(String comentari) {}
}
