package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ExamStatsDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Estadístiques d'un examen per al professor (punt 2.1 de la Fase 3). */
@Service
@RequiredArgsConstructor
public class StatsService {

    static final BigDecimal APROVAT = new BigDecimal("5");
    private static final BigDecimal CENT = new BigDecimal("100");

    private final ExamService examService;
    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public ExamStatsDto stats(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);

        List<ExamSession> totes = sessionRepository.findByExamIdWithDetails(examId);
        List<ExamSession> entregades = totes.stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED).toList();
        List<Question> preguntes = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .sorted(Comparator.comparingInt(Question::getOrdre))
                .toList();

        // Respostes de les sessions entregades, per sessió i pregunta
        Map<UUID, Map<UUID, Answer>> respostes = new HashMap<>();
        List<UUID> ids = entregades.stream().map(ExamSession::getId).toList();
        if (!ids.isEmpty()) {
            for (Answer a : answerRepository.findBySessionIdIn(ids)) {
                respostes.computeIfAbsent(a.getSession().getId(), k -> new HashMap<>())
                        .put(a.getQuestion().getId(), a);
            }
        }

        // Notes dels alumnes
        List<BigDecimal> notes = new ArrayList<>();
        int pendents = 0;
        for (ExamSession s : entregades) {
            Map<UUID, Answer> seves = respostes.getOrDefault(s.getId(), Map.of());
            BigDecimal nota = Puntuacio.notaSobreDeu(preguntes, seves);
            if (nota != null) notes.add(nota);
            for (Question q : preguntes) {
                if (Puntuacio.punts(q, seves.get(q.getId())) == null) pendents++;
            }
        }

        List<ExamStatsDto.Pregunta> perPregunta = preguntes.stream()
                .map(q -> statsPregunta(q, entregades, respostes))
                .toList();

        return new ExamStatsDto(
                entregades.size(),
                totes.size(),
                pendents,
                mitjana(notes),
                mediana(notes),
                notes.stream().min(Comparator.naturalOrder()).map(StatsService::dos).orElse(null),
                notes.stream().max(Comparator.naturalOrder()).map(StatsService::dos).orElse(null),
                notes.isEmpty() ? null : percent(notes.stream().filter(n -> n.compareTo(APROVAT) >= 0).count(), notes.size()),
                histograma(notes),
                perPregunta);
    }

    private ExamStatsDto.Pregunta statsPregunta(Question q, List<ExamSession> entregades,
                                                Map<UUID, Map<UUID, Answer>> respostes) {
        int contestades = 0;
        List<BigDecimal> punts = new ArrayList<>();
        Map<String, Integer> opcions = null;
        int correctes = 0;
        boolean choice = q.getTipus() == QuestionType.CHOICE;
        if (choice) {
            opcions = new LinkedHashMap<>();
            for (String lletra : lletresOpcions(q)) opcions.put(lletra, 0);
        }

        for (ExamSession s : entregades) {
            Answer a = respostes.getOrDefault(s.getId(), Map.of()).get(q.getId());
            boolean contestada = a != null && a.getContingut() != null && !a.getContingut().isBlank();
            if (contestada) contestades++;
            BigDecimal p = Puntuacio.punts(q, a);
            if (p != null) punts.add(p);
            if (choice && contestada) {
                String lletra = a.getContingut().strip().toLowerCase(Locale.ROOT);
                opcions.merge(lletra, 1, Integer::sum);
                if (lletra.equals(q.getCorrectChoice())) correctes++;
            }
        }

        BigDecimal mitjanaPunts = mitjana(punts);
        BigDecimal rendiment = mitjanaPunts == null || q.getPunts().signum() == 0 ? null
                : mitjanaPunts.multiply(CENT).divide(q.getPunts(), 1, RoundingMode.HALF_UP);
        return new ExamStatsDto.Pregunta(
                q.getId(), q.getOrdre(), q.getTipus(), q.getEnunciat(), q.getPunts(), q.isAnulada(), q.getRa(),
                contestades, entregades.size() - contestades,
                mitjanaPunts, rendiment,
                choice && !entregades.isEmpty() ? percent(correctes, entregades.size()) : null,
                choice ? q.getCorrectChoice() : null,
                opcions);
    }

    private static List<String> lletresOpcions(Question q) {
        if (q.getChoices() == null) return List.of();
        return q.getChoices().lines()
                .filter(l -> !l.isBlank())
                .map(l -> l.strip().substring(0, 1).toLowerCase(Locale.ROOT))
                .toList();
    }

    static List<Integer> histograma(List<BigDecimal> notes) {
        Integer[] franges = new Integer[10];
        Arrays.fill(franges, 0);
        for (BigDecimal n : notes) {
            int f = n.setScale(0, RoundingMode.FLOOR).intValue();
            franges[Math.max(0, Math.min(9, f))]++;   // el 10 va a la darrera franja; les negatives, a la primera
        }
        return List.of(franges);
    }

    static BigDecimal mitjana(List<BigDecimal> valors) {
        if (valors.isEmpty()) return null;
        BigDecimal suma = valors.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(valors.size()), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal mediana(List<BigDecimal> valors) {
        if (valors.isEmpty()) return null;
        List<BigDecimal> ordenats = valors.stream().sorted().toList();
        int n = ordenats.size();
        BigDecimal m = n % 2 == 1 ? ordenats.get(n / 2)
                : ordenats.get(n / 2 - 1).add(ordenats.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return dos(m);
    }

    private static BigDecimal percent(long part, long total) {
        return BigDecimal.valueOf(part).multiply(CENT).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal dos(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
