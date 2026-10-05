package com.examplatform.dto;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionDto(
        UUID id,
        int ordre,
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
        String ra,
        String dificultat,
        List<QuestionFileDto> files
) {
    public static QuestionDto from(Question q, List<QuestionFileDto> files) {
        return new QuestionDto(q.getId(), q.getOrdre(), q.getTipus(), q.getEnunciat(),
                q.getPunts(), q.getModelResposta(), q.getOutputContains(),
                q.getOutputExact(), q.getOutputRegex(), q.getTestScript(), q.getClaus(),
                parseChoices(q.getChoices()), q.getCorrectChoice(), q.isBarrejarOpcions(), q.isAnulada(), q.isAmbApunts(),
                q.getRa(), q.getDificultat(), files);
    }

    public static QuestionDto from(Question q) {
        return from(q, List.of());
    }

    public static QuestionDto forStudent(Question q, List<QuestionFileDto> files) {
        return new QuestionDto(q.getId(), q.getOrdre(), q.getTipus(), q.getEnunciat(),
                q.getPunts(), null, null, null, null, null, null,
                parseChoices(q.getChoices()), null, q.isBarrejarOpcions(), q.isAnulada(), q.isAmbApunts(),
                null, null, files);
    }

    public static QuestionDto forStudent(Question q) {
        return forStudent(q, List.of());
    }

    private static List<String> parseChoices(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return List.of(raw.split("\n"));
    }
}
