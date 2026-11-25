package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ResumNotesDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Resum de notes del professor: alumnes aprovats i mitjana de cada examen, i mitjana de cada UT. Usa la mateixa regla
 * de punts que la correcció ({@link Puntuacio}); només compten les sessions entregades.
 */
@Service
@RequiredArgsConstructor
public class ResumNotesService {

    private static final BigDecimal CENT = new BigDecimal("100");

    private final ExamRepository examRepository;
    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;

    private record Clau(String modulNom, String ut) {}

    @Transactional(readOnly = true)
    public ResumNotesDto resum(User professor) {
        List<Exam> exams = examRepository.findGestionablesPer(professor.getId()).stream()
                .sorted(Comparator.comparing(Exam::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ResumNotesDto.Examen> resumExamens = new ArrayList<>();
        Map<Clau, List<BigDecimal>> notesPerUt = new LinkedHashMap<>();
        Map<Clau, Integer> examensPerUt = new HashMap<>();

        for (Exam exam : exams) {
            List<BigDecimal> notes = notesEntregades(exam);
            String modulNom = exam.getModul() != null ? exam.getModul().getNom() : null;
            int aprovats = (int) notes.stream().filter(n -> n.compareTo(StatsService.APROVAT) >= 0).count();
            resumExamens.add(new ResumNotesDto.Examen(exam.getId(), exam.getTitle(), exam.getUt(), modulNom, exam.getStatus(),
                    notes.size(), aprovats, percent(aprovats, notes.size()), StatsService.mitjana(notes)));
            if (exam.getUt() != null && !exam.getUt().isBlank()) {
                Clau k = new Clau(modulNom, exam.getUt());
                notesPerUt.computeIfAbsent(k, x -> new ArrayList<>()).addAll(notes);
                examensPerUt.merge(k, 1, Integer::sum);
            }
        }

        List<ResumNotesDto.Unitat> unitats = new ArrayList<>();
        notesPerUt.forEach((k, notes) -> {
            int aprovats = (int) notes.stream().filter(n -> n.compareTo(StatsService.APROVAT) >= 0).count();
            unitats.add(new ResumNotesDto.Unitat(k.ut(), k.modulNom(), examensPerUt.get(k), notes.size(), aprovats,
                    percent(aprovats, notes.size()), StatsService.mitjana(notes)));
        });
        unitats.sort(Comparator.comparing(ResumNotesDto.Unitat::modulNom, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(ResumNotesDto.Unitat::ut, String::compareToIgnoreCase));
        return new ResumNotesDto(resumExamens, unitats);
    }

    /** Nota sobre 10 de cada alumne que ha entregat l'examen (les respostes sense nota compten 0, com a les estadístiques). */
    private List<BigDecimal> notesEntregades(Exam exam) {
        List<ExamSession> entregades = sessionRepository.findByExamIdWithDetails(exam.getId()).stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED).toList();
        if (entregades.isEmpty()) return List.of();
        List<Question> preguntes = exam.getQuestions().stream().filter(q -> q.getTipus() != QuestionType.SECTION).toList();
        Map<UUID, Map<UUID, Answer>> respostes = new HashMap<>();
        for (Answer a : answerRepository.findBySessionIdIn(entregades.stream().map(ExamSession::getId).toList())) {
            respostes.computeIfAbsent(a.getSession().getId(), k -> new HashMap<>()).put(a.getQuestion().getId(), a);
        }
        List<BigDecimal> notes = new ArrayList<>();
        for (ExamSession s : entregades) {
            BigDecimal nota = Puntuacio.notaSobreDeu(preguntes, respostes.getOrDefault(s.getId(), Map.of()));
            if (nota != null) notes.add(nota);
        }
        return notes;
    }

    private static BigDecimal percent(long part, long total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(part).multiply(CENT).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
