package com.examplatform.domain.service.emmagatzematge;

import java.util.UUID;

/**
 * Espai que ocupa un examen: els fitxers que el professor adjunta a les preguntes i els que
 * els alumnes lliuren. Els identificadors i noms de mòdul, cicle i departament són null si l'examen
 * encara no té mòdul.
 */
public record UsExamen(
        UUID examId, String examTitol,
        UUID professorId, String professor,
        UUID modulId, String modul,
        UUID cicleId, String cicle,
        UUID departamentId, String departament,
        int fitxersPregunta, long midaPregunta,
        int lliuraments, long midaLliuraments) {

    public long total() {
        return midaPregunta + midaLliuraments;
    }
}
