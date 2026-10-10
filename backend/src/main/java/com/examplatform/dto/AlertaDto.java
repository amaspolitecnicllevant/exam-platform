package com.examplatform.dto;

import java.time.LocalDateTime;

/** Una alerta de seguretat per als administradors. */
public record AlertaDto(
        /** ALTA, MITJANA o INFO */
        String gravetat,
        String codi,
        String titol,
        String detall,
        int quantitat,
        LocalDateTime ultima
) {}
