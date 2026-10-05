package com.examplatform.dto;

import com.examplatform.domain.model.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogDto(
        UUID          id,
        UUID          userId,
        String        action,
        String        resource,
        String        ipAddress,
        LocalDateTime createdAt
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(
                a.getId(),
                a.getUserId(),
                a.getAction(),
                a.getResource(),
                a.getIpAddress(),
                a.getCreatedAt()
        );
    }
}
