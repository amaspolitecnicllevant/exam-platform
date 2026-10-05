package com.examplatform.dto;

import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estadístiques d'un examen, calculades sobre les sessions entregades amb la mateixa regla de
 * punts que la correcció (Puntuacio). Els valors null indiquen que no hi ha dades (cap entrega).
 */
public record ExamStatsDto(
        int entregats,
        int sessions,
        /** Respostes encara sense nota: les estadístiques són provisionals (compten 0). */
        int respostesPendents,
        BigDecimal mitjana,
        BigDecimal mediana,
        BigDecimal minima,
        BigDecimal maxima,
        /** Percentatge d'alumnes amb nota >= 5 (0–100). */
        BigDecimal percentAprovats,
        /** Nombre d'alumnes per franja de nota: [0,1), [1,2), … , [9,10]. */
        List<Integer> histograma,
        List<Pregunta> preguntes
) {
    public record Pregunta(
            UUID id,
            int ordre,
            QuestionType tipus,
            String enunciat,
            BigDecimal punts,
            boolean bonus,
            String ra,
            /** Alumnes que han contestat (resposta no buida). */
            int respostes,
            int senseResposta,
            /** Punts mitjans obtinguts (sobre les respostes amb nota). */
            BigDecimal mitjanaPunts,
            /** Rendiment mitjà: mitjanaPunts / punts, en % (0–100). */
            BigDecimal percentRendiment,
            /** CHOICE: % d'alumnes entregats que l'han encertada. Null per a altres tipus. */
            BigDecimal percentCorrectes,
            /** CHOICE: lletra correcta. */
            String correcta,
            /** CHOICE: vegades que s'ha triat cada opció (a, b, c, d), en ordre. */
            Map<String, Integer> opcions
    ) {}
}
