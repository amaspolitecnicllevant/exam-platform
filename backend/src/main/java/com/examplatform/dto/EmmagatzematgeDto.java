package com.examplatform.dto;

import java.util.List;

/** Auditoria de l'espai que ocupen els exàmens, agrupat segons un criteri, i l'estat del disc. */
public record EmmagatzematgeDto(Taula taula, Disc disc) {

    /** @param agrupa criteri d'agrupació (professor, departament, cicle, modul o examen) */
    public record Taula(String agrupa, List<Fila> files, Fila total) {}

    /** Espai d'un grup: fitxers adjunts a les preguntes + fitxers lliurats pels alumnes, en bytes. */
    public record Fila(String clau, String nom, String detall, int examens,
                       int fitxersPregunta, long midaPregunta,
                       int lliuraments, long midaLliuraments, long total) {}

    /**
     * Espai real al disc del servidor.
     * @param midaPreguntesDisc bytes de fitxers de preguntes que hi ha al disc
     * @param midaLliuramentsDisc bytes de lliuraments d'alumnes que hi ha al disc
     * @param espaiLliure bytes lliures a la partició; null si no es pot saber
     * @param espaiTotal mida de la partició; null si no es pot saber
     */
    public record Disc(long midaPreguntesDisc, long midaLliuramentsDisc, Long espaiLliure, Long espaiTotal) {}
}
