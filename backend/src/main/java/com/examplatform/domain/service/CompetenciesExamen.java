package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Competències d'un examen. Són codis lliures (CO1, CO2…): l'examen en pot declarar una llista i cada pregunta en pot
 * portar una. Res no es rebutja; si l'examen en declara i una pregunta en porta una que no hi és, només surt un avís.
 */
public final class CompetenciesExamen {

    /** CO2 abans que CO10. */
    public static final Comparator<String> ORDRE_CODIS =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private CompetenciesExamen() {}

    /** Codis que declara l'examen, o {@code null} si no n'ha declarat cap (llavors no hi ha cap restricció). */
    public static Set<String> permeses(Exam exam) {
        if (exam.getCompetenciesExamen() == null || exam.getCompetenciesExamen().isEmpty()) return null;
        return new LinkedHashSet<>(exam.getCompetenciesExamen().stream().sorted(ORDRE_CODIS).toList());
    }

    /** Preguntes de l'examen amb una competència que l'examen no declara, com a «P3: CO9». */
    public static List<String> problemes(Exam exam) {
        Set<String> permeses = permeses(exam);
        List<String> res = new ArrayList<>();
        if (permeses == null) return res;
        for (Question q : exam.getQuestions()) {
            if (q.getTipus() == QuestionType.SECTION) continue;
            String c = q.getCompetencia();
            if (c != null && !c.isBlank() && !permeses.contains(c.strip())) {
                res.add("P" + Numeracio.numero(q) + ": " + c.strip());
            }
        }
        return res;
    }

    /** Avisos (no bloquejants) sobre les competències de les preguntes. */
    public static List<String> avisos(Exam exam) {
        List<String> fora = problemes(exam);
        if (fora.isEmpty()) return List.of();
        return List.of("Preguntes amb una competència que l'examen no declara (" + String.join(", ", fora) + ").");
    }
}
