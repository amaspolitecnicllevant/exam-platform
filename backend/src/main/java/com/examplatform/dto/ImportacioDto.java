package com.examplatform.dto;

import java.util.List;

/** Resultat d'importar usuaris des d'un CSV. */
public record ImportacioDto(
        /** Comptes nous creats. */
        int created,
        /** Files amb un correu que ja tenia compte (no es modifica: com a molt es matricula o s'afegeix al grup). */
        int skipped,
        /** Matrícules noves (columna modul). */
        int matriculats,
        /** Alumnes afegits a un grup (columna grup). */
        int afegitsAGrup,
        /** Grups que no existien i s'han creat. */
        List<String> grupsCreats,
        List<String> errors,
        /** Contrasenyes generades per a les files que la tenien en blanc: només es mostren ara. */
        List<Credencial> contrasenyes
) {
    public record Credencial(String nom, String email, String contrasenya) {}
}
