package com.examplatform.dto;

import com.examplatform.domain.model.ExamSession;
import com.examplatform.domain.model.SessionStatus;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record SessionDto(
        UUID id,
        UUID examId,
        String examTitle,
        UUID studentId,
        String studentName,
        SessionStatus status,
        Instant startedAt,
        Instant submittedAt,
        int focusLossCount,
        boolean notesVisibles,
        String modulNom,
        List<AnswerDto> answers
) {
    public static SessionDto from(ExamSession s, List<AnswerDto> answers) {
        return new SessionDto(s.getId(), s.getExam().getId(), s.getExam().getTitle(),
                s.getStudent().getId(), s.getStudent().getName(),
                s.getStatus(),
                s.getStartedAt() != null ? s.getStartedAt().toInstant(ZoneOffset.UTC) : null,
                s.getSubmittedAt() != null ? s.getSubmittedAt().toInstant(ZoneOffset.UTC) : null,
                s.getFocusLossCount(),
                s.getExam().isNotesVisibles(),
                s.getExam().getModul() != null ? s.getExam().getModul().getNom() : null,
                answers);
    }
}
