package com.examplatform.dto;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.FormatsFitxer;
import com.examplatform.domain.service.Numeracio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionDto(
        UUID id,
        int ordre,
        /** Número visible (sense comptar les seccions); null a les seccions. */
        Integer numero,
        QuestionType tipus,
        String enunciat,
        BigDecimal punts,
        String modelResposta,
        String outputContains,
        String outputExact,
        String outputRegex,
        String testScript,
        String claus,
        List<String> choices,
        String correctChoice,
        boolean barrejarOpcions,
        boolean anulada,
        boolean ambApunts,
        String competencia,
        String dificultat,
        List<QuestionFileDto> files,
        /** Només FILE_UPLOAD: extensions que l'alumne pot pujar. */
        List<String> formatsPermesos
) {
    public static QuestionDto from(Question q, List<QuestionFileDto> files) {
        return from(q, files, numeroDe(q));
    }

    public static QuestionDto from(Question q, List<QuestionFileDto> files, Integer numero) {
        return new QuestionDto(q.getId(), q.getOrdre(), numero, q.getTipus(), q.getEnunciat(),
                q.getPunts(), q.getModelResposta(), q.getOutputContains(),
                q.getOutputExact(), q.getOutputRegex(), q.getTestScript(), q.getClaus(),
                parseChoices(q.getChoices()), q.getCorrectChoice(), q.isBarrejarOpcions(), q.isAnulada(), q.isAmbApunts(),
                q.getCompetencia(), q.getDificultat(), files, formats(q));
    }

    public static QuestionDto from(Question q) {
        return from(q, List.of());
    }

    public static QuestionDto forStudent(Question q, List<QuestionFileDto> files) {
        return forStudent(q, files, numeroDe(q));
    }

    public static QuestionDto forStudent(Question q, List<QuestionFileDto> files, Integer numero) {
        return new QuestionDto(q.getId(), q.getOrdre(), numero, q.getTipus(), q.getEnunciat(),
                q.getPunts(), null, null, null, null, null, null,
                parseChoices(q.getChoices()), null, q.isBarrejarOpcions(), q.isAnulada(), q.isAmbApunts(),
                null, null, files, formats(q));
    }

    public static QuestionDto forStudent(Question q) {
        return forStudent(q, List.of());
    }

    private static Integer numeroDe(Question q) {
        return q.getTipus() == QuestionType.SECTION ? null : Numeracio.numero(q);
    }

    private static List<String> formats(Question q) {
        return q.getTipus() == QuestionType.FILE_UPLOAD ? FormatsFitxer.deLaPregunta(q.getFormatsPermesos()) : null;
    }

    private static List<String> parseChoices(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return List.of(raw.split("\n"));
    }
}
