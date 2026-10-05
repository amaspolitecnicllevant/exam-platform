package com.examplatform.dto;

import com.examplatform.domain.model.ExamSession;
import com.examplatform.domain.model.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MonitorDto(
        UUID sessionId,
        UUID studentId,
        String studentName,
        String studentEmail,
        String clientIp,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        SessionStatus status,
        int focusLossCount,
        int answersCount
) {
    public static MonitorDto from(ExamSession s) {
        return new MonitorDto(
                s.getId(),
                s.getStudent().getId(),
                s.getStudent().getName(),
                s.getStudent().getEmail(),
                s.getClientIp(),
                s.getStartedAt(),
                s.getSubmittedAt(),
                s.getStatus(),
                s.getFocusLossCount(),
                s.getAnswers().size()
        );
    }
}
