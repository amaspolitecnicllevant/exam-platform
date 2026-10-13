package com.examplatform.domain.service;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Número de pregunta que veuen les persones (pantalles, exportacions, CSV de revisió).
 *
 * <p>{@code Question.ordre} és la posició interna a l'examen i compta també les capçaleres de secció
 * ({@code SECTION}); el número visible les salta, igual que la pantalla de l'alumne.
 */
public final class Numeracio {

    private Numeracio() {}

    /** Número de cada pregunta (sense les seccions, que no hi surten) segons l'ordre intern. */
    public static Map<UUID, Integer> numeros(Collection<Question> preguntes) {
        Map<UUID, Integer> numeros = new HashMap<>();
        int n = 0;
        for (Question q : preguntes.stream().sorted(Comparator.comparingInt(Question::getOrdre)).toList()) {
            if (q.getTipus() != QuestionType.SECTION) numeros.put(q.getId(), ++n);
        }
        return numeros;
    }

    /** Número visible d'una pregunta; si no es coneix l'examen, l'ordre intern. */
    public static int numero(Question q) {
        if (q.getTipus() == QuestionType.SECTION) return q.getOrdre();
        if (q.getExam() == null || q.getExam().getQuestions() == null) return q.getOrdre();
        Integer n = numeros(q.getExam().getQuestions()).get(q.getId());
        return n != null ? n : q.getOrdre();
    }
}
