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
        /** Nota per competència entre els alumnes entregats (buit si cap pregunta no en té). */
        List<Competencia> competencies,
        List<Pregunta> preguntes
) {
    /** Sense estadístiques per competència (p. ex. en els tests que no les necessiten). */
    public ExamStatsDto(int entregats, int sessions, int respostesPendents, BigDecimal mitjana, BigDecimal mediana,
                        BigDecimal minima, BigDecimal maxima, BigDecimal percentAprovats, List<Integer> histograma,
                        List<Pregunta> preguntes) {
        this(entregats, sessions, respostesPendents, mitjana, mediana, minima, maxima, percentAprovats, histograma,
                List.of(), preguntes);
    }

    /** Nota d'una competència (sobre 10) entre els alumnes entregats que hi tenen punts. */
    public record Competencia(
            String codi,
            /** Preguntes de l'examen d'aquesta competència. */
            int preguntes,
            BigDecimal punts,
            /** Punts reals que han tret de mitjana els alumnes entregats (sobre {@code punts}); null si cap alumne n'ha entregat. */
            BigDecimal mitjanaPunts,
            /** Nota mitjana sobre 10; null si cap alumne n'ha entregat. */
            BigDecimal mitjana,
            /** % d'alumnes amb nota >= 5 en aquesta competència (0–100). */
            BigDecimal percentAprovats
    ) {}

    public record Pregunta(
            UUID id,
            int ordre,
            QuestionType tipus,
            String enunciat,
            BigDecimal punts,
            boolean bonus,
            String competencia,
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
