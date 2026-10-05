package com.examplatform.dto;

import java.time.OffsetDateTime;

/** Estat de l'última còpia de seguretat, per a l'administrador. */
public record CopiesSeguretatDto(
        /** Hi ha informació de còpies (el servei "backup" ha fet almenys una còpia). */
        boolean disponible,
        OffsetDateTime data,
        /** "ok" o "error" */
        String resultat,
        String missatge,
        long bytes,
        int retencioDies,
        /** Hores des de l'última còpia (null si no n'hi ha). */
        Long antiguitatHores,
        /** S'ha configurat la còpia a una carpeta compartida (fora de la màquina). */
        boolean remotConfigurat,
        /** "ok" o "error" (null si no està configurada). */
        String remotResultat,
        String remotMissatge,
        /** Cal avisar: no hi ha còpies, l'última ha fallat o és massa antiga. */
        boolean alerta,
        String motiuAlerta
) {}
