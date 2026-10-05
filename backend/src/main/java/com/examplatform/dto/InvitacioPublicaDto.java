package com.examplatform.dto;

import com.examplatform.domain.model.Invitacio;

public record InvitacioPublicaDto(
        String modulCodi,
        String modulNom,
        String cicleNom,
        String departamentNom,
        String curs,
        String professorNom) {

    public static InvitacioPublicaDto from(Invitacio i) {
        return new InvitacioPublicaDto(
                i.getModul().getCodi(),
                i.getModul().getNom(),
                i.getModul().getCicle().getNom(),
                i.getModul().getCicle().getDepartament().getNom(),
                i.getCurs(),
                i.getCreatedBy().getName());
    }
}
