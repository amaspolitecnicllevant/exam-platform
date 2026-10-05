package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSchedulerService {

    private final ExamRepository examRepository;
    private final ExamSessionRepository sessionRepository;
    private final SessionService sessionService;

    @Value("${exam.auto-close-grace-minutes:5}")
    private int autoCloseGraceMinutes;

    /**
     * Marge abans d'entregar d'ofici una sessió exhaurida: el navegador de l'alumne entrega sol en
     * arribar a 0 després d'enviar els últims canvis; si el servidor s'hi avancés, els rebutjaria.
     */
    @Value("${exam.auto-submit-grace-seconds:20}")
    private int autoSubmitGraceSeconds = 20;

    @Scheduled(fixedDelay = 30_000)
    public void closeExpiredSessions() {
        sessionRepository.findExpiredInProgress(autoSubmitGraceSeconds).forEach(s -> {
            try {
                sessionService.forceSubmit(s.getId());
                log.info("Sessió {} enviada automàticament per expiració de temps", s.getId());
            } catch (Exception e) {
                log.error("Error en l'auto-enviament de la sessió {}", s.getId(), e);
            }
        });
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeExpiredExams() {
        LocalDateTime now = LocalDateTime.now();
        examRepository.findExpiredPublished(now).forEach(exam -> {
            // Es tanca quan han passat durada + marge des de l'hora programada...
            LocalDateTime closeAt = exam.getScheduledAt()
                    .plusMinutes(exam.getDurada())
                    .plusMinutes(autoCloseGraceMinutes);
            // ...o abans, si tots els alumnes ja han entregat
            List<ExamSession> sessions = sessionRepository.findByExamIdWithDetails(exam.getId());
            boolean allSubmitted = !sessions.isEmpty()
                    && sessions.stream().allMatch(s -> s.getStatus() == SessionStatus.SUBMITTED);
            if (now.isBefore(closeAt) && !allSubmitted) return;

            exam.setStatus(ExamStatus.CLOSED);
            examRepository.save(exam);
            int entregades = sessionService.entregaEnCurs(exam.getId());
            log.info("Examen «{}» tancat automàticament ({}){}", exam.getTitle(),
                    allSubmitted ? "tots entregats" : "temps exhaurit",
                    entregades > 0 ? ", " + entregades + " sessions en curs entregades" : "");
        });
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void activateScheduled() {
        List<Exam> due = examRepository.findDueForActivation(LocalDateTime.now());
        for (Exam exam : due) {
            // Els solapaments amb altres exàmens programats del grup ja es comproven en programar.
            // No es bloqueja per altres exàmens publicats: n'hi havia prou amb un examen publicat
            // a mà i mai tancat (de qualsevol mòdul) perquè aquest no s'activés, sense cap avís.
            exam.setStatus(ExamStatus.PUBLISHED);
            examRepository.save(exam);

            Set<UUID> existing = new HashSet<>(sessionRepository.findStudentIdsByExamId(exam.getId()));
            exam.getScheduledGrup().getStudents().forEach(student -> {
                if (!existing.contains(student.getId())) {
                    sessionRepository.save(
                            ExamSession.builder().exam(exam).student(student).build());
                }
            });

            log.info("Examen «{}» activat automàticament per al grup «{}»",
                    exam.getTitle(), exam.getScheduledGrup().getName());
        }
    }
}
