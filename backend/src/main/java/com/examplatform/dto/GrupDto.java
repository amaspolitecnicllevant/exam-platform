package com.examplatform.dto;

import com.examplatform.domain.model.Grup;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GrupDto(
        UUID id,
        String name,
        UUID modulId,
        String modulNom,
        List<UserDto> students,
        LocalDateTime createdAt
) {
    public static GrupDto from(Grup g) {
        List<UserDto> students = g.getStudents().stream()
                .map(UserDto::from)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        return new GrupDto(g.getId(), g.getName(),
                g.getModul() != null ? g.getModul().getId() : null,
                g.getModul() != null ? g.getModul().getNom() : null,
                students, g.getCreatedAt());
    }

    public record CreateRequest(@NotBlank String name) {}

    public record AddStudentsRequest(List<UUID> studentIds) {}
}
