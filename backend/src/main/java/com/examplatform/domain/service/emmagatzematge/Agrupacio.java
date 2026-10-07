package com.examplatform.domain.service.emmagatzematge;

import java.util.Locale;

/** Criteri per agrupar l'espai ocupat. */
public enum Agrupacio {
    PROFESSOR, DEPARTAMENT, CICLE, MODUL, EXAMEN;

    /** Interpreta el valor de la petició («professor», «Modul»…); IllegalArgumentException si no existeix. */
    public static Agrupacio de(String text) {
        if (text == null || text.isBlank()) return PROFESSOR;
        String t = text.strip().toUpperCase(Locale.ROOT).replace("Ò", "O").replace("Ó", "O");
        for (Agrupacio a : values()) if (a.name().equals(t)) return a;
        throw new IllegalArgumentException("Agrupació desconeguda: «" + text
                + "». Opcions: professor, departament, cicle, modul, examen");
    }
}
