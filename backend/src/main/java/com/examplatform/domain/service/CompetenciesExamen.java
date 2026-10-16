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
 * Quines competències es proposen per a les preguntes d'un examen: les que declara l'examen; si no n'ha declarat, les de les
 * UT que avalua; si no té UT, les del seu mòdul; si no té mòdul (o el mòdul no té programació), cap restricció.
 * Res no es rebutja: un codi que no és a la programació només genera un avís ({@link #avisos}).
 */
public final class CompetenciesExamen {

    /** CO2 abans que CO10. */
    public static final Comparator<String> ORDRE_CODIS =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private CompetenciesExamen() {}

    /** Codis proposats, o {@code null} si no hi ha cap restricció. */
    public static Set<String> permeses(Exam exam) {
        if (exam.getCompetenciesExamen() != null && !exam.getCompetenciesExamen().isEmpty()) {
            return new LinkedHashSet<>(exam.getCompetenciesExamen().stream().sorted(ORDRE_CODIS).toList());
        }
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

    /** Preguntes de l'examen amb una competència fora de les proposades, com a «P3: CO9». */
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

    /** Avisos (no bloquejants) sobre les competències de l'examen i de les seves preguntes. */
    public static List<String> avisos(Exam exam) {
        List<String> avisos = new ArrayList<>();
        if (exam.getModul() != null) {
            Set<String> programades = new LinkedHashSet<>();
            exam.getModul().getCompetencies().forEach(c -> programades.add(c.getCodi()));
            Set<String> desconegudes = new LinkedHashSet<>();
            exam.getCompetenciesExamen().stream().sorted(ORDRE_CODIS).forEach(desconegudes::add);
            exam.getQuestions().stream().filter(q -> q.getTipus() != QuestionType.SECTION)
                    .map(Question::getCompetencia).filter(c -> c != null && !c.isBlank()).map(String::strip)
                    .sorted(ORDRE_CODIS).forEach(desconegudes::add);
            desconegudes.removeAll(programades);
            if (!desconegudes.isEmpty()) {
                avisos.add("Aquestes competències no són a la programació del mòdul " + exam.getModul().getCodi() + ": "
                        + String.join(", ", desconegudes) + (programades.isEmpty()
                        ? ". El mòdul encara no té programació."
                        : ". Afegeix-les a la programació o corregeix-les."));
            }
        }
        // Les que no són a la programació ja s'han avisat; aquí només les que sí que hi són però l'examen no proposa
        Set<String> programades = exam.getModul() == null ? Set.of()
                : exam.getModul().getCompetencies().stream().map(Competencia::getCodi).collect(java.util.stream.Collectors.toSet());
        List<String> fora = problemes(exam).stream()
                .filter(p -> exam.getModul() == null || programades.contains(p.substring(p.indexOf(": ") + 2))).toList();
        if (!fora.isEmpty()) {
            avisos.add(exam.getCompetenciesExamen().isEmpty()
                    ? "Preguntes amb una competència que no és de les unitats triades (" + String.join(", ", fora) + ")."
                    : "Preguntes amb una competència que l'examen no declara (" + String.join(", ", fora) + ").");
        }
        return avisos;
    }
}
