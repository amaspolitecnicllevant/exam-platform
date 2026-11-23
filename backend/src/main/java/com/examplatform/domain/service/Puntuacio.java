package com.examplatform.domain.service;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
        if (q.isAnulada()) return maximPregunta(q);
        if (a == null) return BigDecimal.ZERO;
        BigDecimal brut = a.getManualScore() != null ? a.getManualScore() : a.getAutoScore();
        return brut == null ? null : escala(brut, factor(q));
    }

    /**
     * Punts màxims d'una pregunta un cop aplicat el pes de la seva part. Sense pesos és simplement
     * {@code q.getPunts()}. Les notes que es desen a les respostes són sempre sobre els punts «bruts» de la pregunta.
     */
    public static BigDecimal maximPregunta(Question q) {
        return escala(q.getPunts(), factor(q));
    }

    /**
     * Factor pel qual s'escalen els punts de les preguntes d'una part perquè la part valgui el seu pes (%) sobre el
     * total de punts de l'examen: {@code pes/100 · puntsTotals / puntsDeLaPart}. 1 si l'examen no té pesos.
     */
    public static BigDecimal factor(Question q) {
        if (q.getTipus() == QuestionType.SECTION || q.getExam() == null || q.getExam().getQuestions() == null) {
            return BigDecimal.ONE;
        }
        List<Question> totes = q.getExam().getQuestions().stream()
                .sorted(Comparator.comparingInt(Question::getOrdre)).toList();
        Question seccio = null, seccioDeQ = null;
        BigDecimal totalPunts = BigDecimal.ZERO, puntsPart = BigDecimal.ZERO;
        for (Question x : totes) {
            if (x.getTipus() == QuestionType.SECTION) {
                seccio = x;
                continue;
            }
            totalPunts = totalPunts.add(x.getPunts());
            if (x.getId() != null ? x.getId().equals(q.getId()) : x == q) seccioDeQ = seccio;
        }
        if (seccioDeQ == null || seccioDeQ.getPesPart() == null) return BigDecimal.ONE;
        seccio = null;
        for (Question x : totes) {
            if (x.getTipus() == QuestionType.SECTION) seccio = x;
            else if (seccio == seccioDeQ) puntsPart = puntsPart.add(x.getPunts());
        }
        if (puntsPart.signum() <= 0) return BigDecimal.ONE;
        return seccioDeQ.getPesPart().multiply(totalPunts)
                .divide(puntsPart.multiply(BigDecimal.valueOf(100)), 8, RoundingMode.HALF_UP);
    }

    /** Una nota «bruta» (sobre els punts de la pregunta) expressada amb el pes de la seva part. */
    public static BigDecimal puntsEscalats(Question q, BigDecimal brut) {
        return brut == null ? null : escala(brut, factor(q));
    }

    private static BigDecimal escala(BigDecimal valor, BigDecimal factor) {
        return factor.compareTo(BigDecimal.ONE) == 0 ? valor : valor.multiply(factor).setScale(6, RoundingMode.HALF_UP);
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
                .map(Puntuacio::maximPregunta).reduce(BigDecimal.ZERO, BigDecimal::add);
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
