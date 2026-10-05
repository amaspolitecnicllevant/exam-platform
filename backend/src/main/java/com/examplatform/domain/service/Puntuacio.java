package com.examplatform.domain.service;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Regla única dels punts d'una pregunta (també la replica el frontend a src/notes.ts). */
public final class Puntuacio {

    private Puntuacio() {}

    /**
     * Punts que compten per a una pregunta:
     * <ul>
     *   <li>bonus (anul·lada): els punts sencers per a tothom, hagi respost o no;</li>
     *   <li>altrament, la nota revisada o, si no n'hi ha, la proposta automàtica;</li>
     *   <li>sense resposta: 0.</li>
     * </ul>
     * Retorna {@code null} si hi ha resposta però encara no té cap nota.
     */
    public static BigDecimal punts(Question q, Answer a) {
        if (q.getTipus() == QuestionType.SECTION) return BigDecimal.ZERO;
        if (q.isAnulada()) return q.getPunts();
        if (a == null) return BigDecimal.ZERO;
        if (a.getManualScore() != null) return a.getManualScore();
        return a.getAutoScore();
    }

    /** Nota total d'un alumne (les respostes sense nota compten 0). */
    public static BigDecimal total(Collection<Question> preguntes, Map<UUID, Answer> respostesPerPregunta) {
        BigDecimal total = BigDecimal.ZERO;
        for (Question q : preguntes) {
            BigDecimal p = punts(q, respostesPerPregunta.get(q.getId()));
            if (p != null) total = total.add(p);
        }
        return total;
    }

    /** Punts màxims de l'examen (sense comptar les seccions). */
    public static BigDecimal maxim(Collection<Question> preguntes) {
        return preguntes.stream().filter(q -> q.getTipus() != QuestionType.SECTION)
                .map(Question::getPunts).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Nota sobre 10, arrodonida a 2 decimals: així es poden comparar exàmens de puntuacions diferents
     * i aplicar l'aprovat (5). Null si l'examen no té punts.
     */
    public static BigDecimal sobreDeu(BigDecimal punts, BigDecimal maxim) {
        if (maxim == null || maxim.signum() <= 0) return null;
        return punts.multiply(BigDecimal.TEN).divide(maxim, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Nota sobre 10 d'un alumne (les respostes sense nota compten 0). */
    public static BigDecimal notaSobreDeu(Collection<Question> preguntes, Map<UUID, Answer> respostesPerPregunta) {
        return sobreDeu(total(preguntes, respostesPerPregunta), maxim(preguntes));
    }
}
