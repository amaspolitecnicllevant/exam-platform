package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(MockitoExtension.class)
class ExamSchedulerServiceTest {

    @Mock ExamRepository        examRepository;
    @Mock ExamSessionRepository sessionRepository;
    @Mock SessionService        sessionService;

    ExamSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new ExamSchedulerService(examRepository, sessionRepository, sessionService);
    }

    // ── activateScheduled ─────────────────────────────────────────────────────

    @Test
    void activateScheduled_publicaExamen_i_crea_sessions_als_alumnes_sense_sessio() {
        User a1 = student(); User a2 = student();
        Grup grup = grup(a1, a2);
        Exam exam = examProgramat(grup);

        when(examRepository.findDueForActivation(any())).thenReturn(List.of(exam));
        when(sessionRepository.findStudentIdsByExamId(exam.getId()))
                .thenReturn(List.of(a1.getId()));  // a1 ja té sessió, a2 no
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activateScheduled();

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        // Només a2 ha de rebre una nova sessió
        verify(sessionRepository, times(1)).save(argThat(s ->
                s.getStudent().getId().equals(a2.getId())));
    }

    @Test
    void activateScheduled_s_activa_encara_que_hi_hagi_un_altre_examen_publicat() {
        // Abans, un examen publicat a mà i mai tancat bloquejava l'activació sense avisar ningú
        User a1 = student();
        Grup grup = grup(a1);
        Exam exam = examProgramat(grup);
        when(examRepository.findDueForActivation(any())).thenReturn(List.of(exam));
        when(sessionRepository.findStudentIdsByExamId(exam.getId())).thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activateScheduled();

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        verify(sessionRepository).save(argThat(s -> s.getStudent().getId().equals(a1.getId())));
    }

    @Test
    void activateScheduled_sensePendents_noFaRes() {
        when(examRepository.findDueForActivation(any())).thenReturn(List.of());

        service.activateScheduled();

        verify(sessionRepository, never()).save(any());
        verify(examRepository, never()).save(any());
    }

    // ── closeExpiredExams ─────────────────────────────────────────────────────

    private Exam examEnCurs(LocalDateTime programat) {
        return Exam.builder().id(UUID.randomUUID()).title("En curs").durada(60).status(ExamStatus.PUBLISHED)
                .scheduledAt(programat).createdBy(student()).penalitzacioChoice(BigDecimal.ZERO).build();
    }

    private ExamSession sessio(Exam exam, SessionStatus estat) {
        return ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(student())
                .startedAt(LocalDateTime.now().minusMinutes(10)).status(estat).build();
    }

    @Test
    void closeExpiredExams_temps_exhaurit_tanca_i_entrega_les_sessions_en_curs() {
        Exam exam = examEnCurs(LocalDateTime.now().minusMinutes(70));   // 60 + 5 de marge ja passats
        when(examRepository.findExpiredPublished(any())).thenReturn(List.of(exam));
        when(sessionRepository.findByExamIdWithDetails(exam.getId()))
                .thenReturn(List.of(sessio(exam, SessionStatus.IN_PROGRESS)));

        service.closeExpiredExams();

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.CLOSED);
        verify(sessionService).entregaEnCurs(exam.getId());
    }

    @Test
    void closeExpiredExams_tanca_abans_si_tots_han_entregat() {
        Exam exam = examEnCurs(LocalDateTime.now().minusMinutes(20));
        when(examRepository.findExpiredPublished(any())).thenReturn(List.of(exam));
        when(sessionRepository.findByExamIdWithDetails(exam.getId()))
                .thenReturn(List.of(sessio(exam, SessionStatus.SUBMITTED), sessio(exam, SessionStatus.SUBMITTED)));

        service.closeExpiredExams();

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.CLOSED);
    }

    @Test
    void closeExpiredExams_dins_del_temps_amb_alumnes_fent_lo_no_tanca() {
        Exam exam = examEnCurs(LocalDateTime.now().minusMinutes(20));
        when(examRepository.findExpiredPublished(any())).thenReturn(List.of(exam));
        when(sessionRepository.findByExamIdWithDetails(exam.getId()))
                .thenReturn(List.of(sessio(exam, SessionStatus.SUBMITTED), sessio(exam, SessionStatus.IN_PROGRESS)));

        service.closeExpiredExams();

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        verify(examRepository, never()).save(any());
        verify(sessionService, never()).entregaEnCurs(any());
    }

    // ── closeExpiredSessions ──────────────────────────────────────────────────

    @Test
    void closeExpiredSessions_crida_forceSubmit_per_cada_sessio_expirada() {
        ExamSession s1 = sessionExpirada();
        ExamSession s2 = sessionExpirada();
        when(sessionRepository.findExpiredInProgress(anyInt())).thenReturn(List.of(s1, s2));

        service.closeExpiredSessions();

        verify(sessionService).forceSubmit(s1.getId());
        verify(sessionService).forceSubmit(s2.getId());
    }

    @Test
    void closeExpiredSessions_error_en_una_sessio_continua_amb_les_altres() {
        ExamSession s1 = sessionExpirada();
        ExamSession s2 = sessionExpirada();
        when(sessionRepository.findExpiredInProgress(anyInt())).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("error BD")).when(sessionService).forceSubmit(s1.getId());

        service.closeExpiredSessions();

        // Ha de processar s2 malgrat l'error a s1
        verify(sessionService).forceSubmit(s2.getId());
    }

    @Test
    void closeExpiredSessions_senseSessionsExpirades_noFaRes() {
        when(sessionRepository.findExpiredInProgress(anyInt())).thenReturn(List.of());

        service.closeExpiredSessions();

        verifyNoInteractions(sessionService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User student() {
        return User.builder().id(UUID.randomUUID()).role(Role.STUDENT)
                .email(UUID.randomUUID() + "@test.cat").name("A").build();
    }

    private Grup grup(User... students) {
        return Grup.builder().id(UUID.randomUUID()).name("G1")
                .createdBy(User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR)
                        .email("prof@test.cat").name("Prof").build())
                .students(new HashSet<>(Arrays.asList(students))).build();
    }

    private Exam examProgramat(Grup grup) {
        Exam e = Exam.builder().id(UUID.randomUUID()).title("Test")
                .durada(60).status(ExamStatus.DRAFT)
                .scheduledAt(LocalDateTime.now().minusMinutes(1))
                .scheduledGrup(grup)
                .createdBy(grup.getCreatedBy())
                .penalitzacioChoice(BigDecimal.ZERO).build();
        return e;
    }

    private Exam examPublicat(Grup grup) {
        Exam e = Exam.builder().id(UUID.randomUUID()).title("Publicat")
                .durada(60).status(ExamStatus.PUBLISHED)
                .createdBy(grup.getCreatedBy())
                .penalitzacioChoice(BigDecimal.ZERO).build();
        return e;
    }

    private ExamSession sessionExpirada() {
        Exam exam = Exam.builder().id(UUID.randomUUID()).title("Test")
                .durada(60).status(ExamStatus.PUBLISHED)
                .createdBy(student()).penalitzacioChoice(BigDecimal.ZERO).build();
        return ExamSession.builder().id(UUID.randomUUID())
                .exam(exam).student(student())
                .startedAt(LocalDateTime.now().minusMinutes(90))
                .build();
    }
}
