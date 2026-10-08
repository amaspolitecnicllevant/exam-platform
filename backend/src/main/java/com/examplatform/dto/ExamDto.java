package com.examplatform.dto;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ExamDto(
        UUID id,
        String title,
        int durada,
        String instruccions,
        ExamStatus status,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime scheduledAt,
        UUID scheduledGrupId,
        String scheduledGrupName,
        BigDecimal penalitzacioChoice,
        UUID modulId,
        String modulNom,
        String cicleNom,
        UUID aulaId,
        String aulaNom,
        String aulaCidr,
        boolean notesVisibles,
        boolean unaPreguntaPerPantalla,
        boolean restringit,
        boolean teSessions,
        List<QuestionDto> questions
) {
    /** Còpia amb la marca «ja té sessions d'alumnes» (només la calcula el llistat del professor). */
    public ExamDto ambSessions(boolean teSessions) {
        return new ExamDto(id, title, durada, instruccions, status, createdByName, createdAt, scheduledAt,
                scheduledGrupId, scheduledGrupName, penalitzacioChoice, modulId, modulNom, cicleNom, aulaId,
                aulaNom, aulaCidr, notesVisibles, unaPreguntaPerPantalla, restringit, teSessions, questions);
    }

    public static ExamDto from(Exam e, boolean includeAnswers) {
        return from(e, includeAnswers, Map.of());
    }

    public static ExamDto from(Exam e, boolean includeAnswers,
                                Map<UUID, List<QuestionFileDto>> filesMap) {
        List<QuestionDto> qs = e.getQuestions().stream()
                .map(q -> {
                    List<QuestionFileDto> files = filesMap.getOrDefault(q.getId(), List.of());
                    return includeAnswers ? QuestionDto.from(q, files) : QuestionDto.forStudent(q, files);
                })
                .toList();
        return new ExamDto(e.getId(), e.getTitle(), e.getDurada(), e.getInstruccions(),
                e.getStatus(), e.getCreatedBy().getName(), e.getCreatedAt(),
                e.getScheduledAt(),
                e.getScheduledGrup() != null ? e.getScheduledGrup().getId() : null,
                e.getScheduledGrup() != null ? e.getScheduledGrup().getName() : null,
                e.getPenalitzacioChoice() != null ? e.getPenalitzacioChoice() : BigDecimal.ZERO,
                e.getModul() != null ? e.getModul().getId() : null,
                e.getModul() != null ? e.getModul().getNom() : null,
                e.getModul() != null ? e.getModul().getCicle().getNom() : null,
                e.getAula() != null ? e.getAula().getId() : null,
                e.getAula() != null ? e.getAula().getNom() : null,
                e.getAula() != null ? e.getAula().getXarxaCidr() : null,
                e.isNotesVisibles(),
                e.isUnaPreguntaPerPantalla(),
                e.isRestringit(),
                false,
                qs);
    }
}
