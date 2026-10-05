package com.examplatform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un examen entregat de l'historial de l'alumne. La nota només hi és si el professor ha
 * publicat les notes d'aquell examen.
 */
public record HistorialDto(
        UUID sessionId,
        UUID examId,
        String examTitle,
        UUID modulId,
        String modulNom,
        Instant submittedAt,
        boolean notesVisibles,
        BigDecimal nota
) {}
