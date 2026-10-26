package com.examplatform.domain.service;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamSession;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.model.SessionStatus;
import com.examplatform.dto.ProgresDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Agrupa, per mòdul i competència, el que un alumne ha suspès en diferents exàmens.
 *
 * <p>Només compten les sessions entregades d'exàmens amb les notes publicades. Una competència d'un examen està
 * suspesa si la seva nota (sobre 10) és inferior a {@link #APROVAT}; si encara hi ha preguntes d'aquella competència
 * pendents de correcció, l'examen no hi compta (la nota no seria definitiva). Les preguntes sense competència no hi
 * surten.
 */
public final class ProgresAlumne {

    public static final BigDecimal APROVAT = BigDecimal.valueOf(5);
    static final int MAX_ENUNCIAT = 160;

    private ProgresAlumne() {}

    private static final class Acumulat {
        BigDecimal punts = BigDecimal.ZERO;
        BigDecimal obtinguts = BigDecimal.ZERO;
        final List<ProgresDto.Examen> suspesos = new ArrayList<>();
    }

    public static ProgresDto calcula(List<ExamSession> sessions, Map<UUID, Map<UUID, Answer>> respostes) {
        List<ExamSession> valides = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED && s.getExam().isNotesVisibles())
                .sorted(Comparator.comparing(ExamSession::getSubmittedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        // mòdul → (codi de competència → acumulat); TreeMap per tenir les competències en ordre natural
        Map<String, Map<String, Acumulat>> perModul = new LinkedHashMap<>();
        Map<String, String> nomsModul = new LinkedHashMap<>();
        Map<String, UUID> idsModul = new LinkedHashMap<>();

        for (ExamSession s : valides) {
            Exam exam = s.getExam();
            String clauModul = exam.getModul() != null ? exam.getModul().getId().toString() : "";
            nomsModul.putIfAbsent(clauModul, exam.getModul() != null ? exam.getModul().getNom() : null);
            idsModul.putIfAbsent(clauModul, exam.getModul() != null ? exam.getModul().getId() : null);
            Map<String, Acumulat> competencies = perModul.computeIfAbsent(clauModul,
                    k -> new TreeMap<>(CompetenciesExamen.ORDRE_CODIS));
            Map<UUID, Answer> resp = respostes.getOrDefault(s.getId(), Map.of());
            Map<UUID, Integer> numeros = Numeracio.numeros(exam.getQuestions());

            Map<String, List<Question>> perCodi = new LinkedHashMap<>();
            for (Question q : exam.getQuestions()) {
                String codi = codi(q);
                if (codi != null) perCodi.computeIfAbsent(codi, k -> new ArrayList<>()).add(q);
            }

            for (Map.Entry<String, List<Question>> e : perCodi.entrySet()) {
                BigDecimal max = BigDecimal.ZERO, obtinguts = BigDecimal.ZERO;
                boolean pendent = false;
                List<ProgresDto.Pregunta> fallades = new ArrayList<>();
                for (Question q : e.getValue()) {
                    BigDecimal p = Puntuacio.punts(q, resp.get(q.getId()));
                    if (p == null) { pendent = true; continue; }
                    max = max.add(q.getPunts());
                    obtinguts = obtinguts.add(p);
                    if (p.compareTo(q.getPunts()) < 0) {
                        fallades.add(new ProgresDto.Pregunta(numeros.getOrDefault(q.getId(), q.getOrdre()), q.getTipus(),
                                resumEnunciat(q.getEnunciat()), q.getPunts(), p));
                    }
                }
                if (pendent) continue;
                Acumulat a = competencies.computeIfAbsent(e.getKey(), k -> new Acumulat());
                a.punts = a.punts.add(max);
                a.obtinguts = a.obtinguts.add(obtinguts);
                BigDecimal nota = Puntuacio.sobreDeu(obtinguts, max);
                if (nota != null && nota.compareTo(APROVAT) < 0) {
                    fallades.sort(Comparator.comparingInt(ProgresDto.Pregunta::numero));
                    Instant data = s.getSubmittedAt() != null ? s.getSubmittedAt().toInstant(ZoneOffset.UTC) : null;
                    a.suspesos.add(new ProgresDto.Examen(s.getId(), exam.getId(), exam.getTitle(), data, nota, fallades));
                }
            }
        }

        List<ProgresDto.Modul> moduls = new ArrayList<>();
        for (Map.Entry<String, Map<String, Acumulat>> m : perModul.entrySet()) {
            List<ProgresDto.Competencia> cs = new ArrayList<>();
            for (Map.Entry<String, Acumulat> c : m.getValue().entrySet()) {
                if (c.getValue().suspesos.isEmpty()) continue;
                cs.add(new ProgresDto.Competencia(c.getKey(), Puntuacio.sobreDeu(c.getValue().obtinguts, c.getValue().punts),
                        c.getValue().suspesos));
            }
            if (!cs.isEmpty()) moduls.add(new ProgresDto.Modul(idsModul.get(m.getKey()), nomsModul.get(m.getKey()), cs));
        }
        moduls.sort(Comparator.comparing(m -> m.modulNom() == null ? "" : m.modulNom(), String.CASE_INSENSITIVE_ORDER));
        return new ProgresDto(moduls);
    }

    private static String codi(Question q) {
        if (q.getTipus() == QuestionType.SECTION) return null;
        String c = q.getCompetencia();
        return c == null || c.isBlank() ? null : c.strip().toUpperCase();
    }

    /** Primera línia no buida de l'enunciat, retallada. */
    static String resumEnunciat(String enunciat) {
        if (enunciat == null) return "";
        String linia = enunciat.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        return linia.length() <= MAX_ENUNCIAT ? linia : linia.substring(0, MAX_ENUNCIAT - 1).stripTrailing() + "…";
    }
}
