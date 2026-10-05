package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.GrupDto;
import com.examplatform.dto.RecuperacioDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.GrupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Recuperació d'un examen: llista els alumnes suspesos i no presentats i en crea un grup, per
 * programar-hi la recuperació (un examen duplicat) només per a ells.
 */
@Service
@RequiredArgsConstructor
public class RecuperacioService {

    private final ExamService examService;
    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;
    private final GrupRepository grupRepository;

    @Transactional(readOnly = true)
    public RecuperacioDto candidats(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);

        List<Question> preguntes = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION).toList();

        List<ExamSession> sessions = sessionRepository.findByExamIdWithDetails(examId);
        Map<UUID, Map<UUID, Answer>> respostes = new HashMap<>();
        List<UUID> entregades = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED).map(ExamSession::getId).toList();
        if (!entregades.isEmpty()) {
            for (Answer a : answerRepository.findBySessionIdIn(entregades)) {
                respostes.computeIfAbsent(a.getSession().getId(), k -> new HashMap<>())
                        .put(a.getQuestion().getId(), a);
            }
        }

        int pendents = 0;
        List<RecuperacioDto.Candidat> candidats = new ArrayList<>();
        for (ExamSession s : sessions) {
            User alumne = s.getStudent();
            if (s.getStatus() != SessionStatus.SUBMITTED) {
                candidats.add(new RecuperacioDto.Candidat(alumne.getId(), alumne.getName(), alumne.getEmail(),
                        RecuperacioDto.Motiu.NO_PRESENTAT, null, false));
                continue;
            }
            Map<UUID, Answer> seves = respostes.getOrDefault(s.getId(), Map.of());
            long senseNota = preguntes.stream().filter(q -> Puntuacio.punts(q, seves.get(q.getId())) == null).count();
            pendents += (int) senseNota;
            BigDecimal nota = Puntuacio.notaSobreDeu(preguntes, seves);
            if (nota != null && nota.compareTo(StatsService.APROVAT) < 0) {
                candidats.add(new RecuperacioDto.Candidat(alumne.getId(), alumne.getName(), alumne.getEmail(),
                        RecuperacioDto.Motiu.SUSPES, nota, senseNota > 0));
            }
        }
        candidats.sort(Comparator.comparing(RecuperacioDto.Candidat::motiu)
                .thenComparing(RecuperacioDto.Candidat::nom, String.CASE_INSENSITIVE_ORDER));
        return new RecuperacioDto(pendents, candidats);
    }

    /**
     * Crea un grup (del mòdul de l'examen) amb els alumnes triats. Només s'hi poden posar alumnes que
     * tenen sessió en aquest examen, perquè ningú no pugui afegir-hi alumnes d'altres mòduls.
     */
    @Transactional
    public GrupDto crearGrup(UUID examId, RecuperacioDto.CrearGrupRequest req, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        String nom = req.nom() == null ? "" : req.nom().strip();
        if (nom.isEmpty()) throw new IllegalArgumentException("El nom del grup no pot estar buit");
        if (nom.length() > 255) throw new IllegalArgumentException("El nom del grup pot tenir com a màxim 255 caràcters");
        if (req.alumneIds() == null || req.alumneIds().isEmpty()) {
            throw new IllegalArgumentException("Tria almenys un alumne");
        }

        Map<UUID, User> delExamen = new HashMap<>();
        sessionRepository.findByExamIdWithDetails(examId).forEach(s -> delExamen.put(s.getStudent().getId(), s.getStudent()));
        Set<User> alumnes = new HashSet<>();
        for (UUID id : req.alumneIds()) {
            User u = delExamen.get(id);
            if (u == null) throw new IllegalArgumentException("Hi ha alumnes que no han fet aquest examen");
            alumnes.add(u);
        }

        Grup grup = Grup.builder().name(nom).createdBy(user).modul(exam.getModul()).students(alumnes).build();
        return GrupDto.from(grupRepository.save(grup));
    }
}
