package com.examplatform.dto;

import com.examplatform.domain.model.Invitacio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitacioDto(
        UUID id,
        UUID token,
        UUID modulId,
        String modulCodi,
        String modulNom,
        String cicleNom,
        String curs,
        String createdByNom,
        LocalDateTime expiresAt,
        int usesCount,
        Integer maxUses,
        boolean active,
        UUID grupId,
        String grupNom) {

    public static InvitacioDto from(Invitacio i) {
        return new InvitacioDto(
                i.getId(), i.getToken(),
                i.getModul().getId(), i.getModul().getCodi(), i.getModul().getNom(),
                i.getModul().getCicle().getNom(),
                i.getCurs(), i.getCreatedBy().getName(),
                i.getExpiresAt(), i.getUsesCount(), i.getMaxUses(), i.isActive(),
                i.getGrup() != null ? i.getGrup().getId() : null,
                i.getGrup() != null ? i.getGrup().getName() : null);
    }

    public record CreateRequest(
            @NotNull UUID modulId,
            @NotBlank String curs,
            Integer maxUses,
            UUID grupId) {}
}
