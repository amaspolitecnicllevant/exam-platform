package com.examplatform.domain.service;

import com.examplatform.domain.model.Competencia;
import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.model.UnitatTreball;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quines competències es poden assignar a les preguntes d'un examen: les de les UT que avalua; si no té UT, les del seu mòdul;
 * si no té mòdul (o el mòdul no té programació), qualsevol codi (encara no es pot validar).
 */
public final class CompetenciesExamen {

    private CompetenciesExamen() {}

    /** Codis permesos, o {@code null} si no hi ha cap restricció. */
    public static Set<String> permeses(Exam exam) {
        if (exam.getUnitats() != null && !exam.getUnitats().isEmpty()) {
            Set<String> codis = new LinkedHashSet<>();
            for (UnitatTreball u : exam.getUnitats()) {
                u.getCompetencies().stream().sorted(Comparator.comparingInt(Competencia::getOrdre))
                        .forEach(c -> codis.add(c.getCodi()));
            }
            return codis;
        }
        if (exam.getModul() != null && !exam.getModul().getCompetencies().isEmpty()) {
            Set<String> codis = new LinkedHashSet<>();
            exam.getModul().getCompetencies().forEach(c -> codis.add(c.getCodi()));
            return codis;
        }
        return null;
    }

    /** Rebutja un codi que no és de cap competència permesa. Un codi buit (sense competència) sempre és vàlid. */
    public static void valida(Exam exam, String codi) {
        if (codi == null || codi.isBlank()) return;
        Set<String> permeses = permeses(exam);
        if (permeses != null && !permeses.contains(codi.strip())) {
            throw new IllegalArgumentException("La competència «" + codi.strip() + "» no és una de les permeses en aquest examen ("
                    + String.join(", ", permeses) + ")");
        }
    }

    /** Preguntes de l'examen amb una competència no permesa, com a «P3: CO9». */
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
}
