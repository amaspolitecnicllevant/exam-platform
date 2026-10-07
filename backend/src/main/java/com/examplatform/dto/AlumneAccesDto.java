package com.examplatform.dto;

import java.util.List;
import java.util.UUID;

/** Alumne amb accés assignat a un examen restringit, i en quin punt és. */
public record AlumneAccesDto(UUID id, String nom, String email, Estat estat) {

    public enum Estat { PENDENT, EN_CURS, ENTREGAT }

    /** Resposta de la llista d'alumnes d'un examen: si és restringit i qui hi té accés. */
    public record Llista(boolean restringit, List<AlumneAccesDto> alumnes) {}
}
