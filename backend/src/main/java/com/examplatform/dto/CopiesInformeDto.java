package com.examplatform.dto;

import com.examplatform.domain.model.QuestionType;

import java.util.List;
import java.util.UUID;

/** Informe de possibles còpies d'un examen (només per al professor). */
public record CopiesInformeDto(
        int llindarPercent,
        int llindarApuntsPercent,
        int minErradesTest,
        int entregats,
        List<Parella> parelles
) {
    public record Parella(
            UUID sessioA, String alumneA,
            UUID sessioB, String alumneB,
            int maxSemblanca,
            List<Coincidencia> coincidencies,
            /** Ordre de les preguntes de test en què tots dos han triat la mateixa opció incorrecta. */
            List<Integer> erradesTestComunes
    ) {}

    public record Coincidencia(
            UUID preguntaId, int ordre, QuestionType tipus, boolean ambApunts, String enunciat, int semblanca,
            String respostaA, String respostaB,
            /** Intervals [inici, fi) de text coincident a cada resposta, per ressaltar-los. */
            List<int[]> marquesA, List<int[]> marquesB
    ) {}
}
