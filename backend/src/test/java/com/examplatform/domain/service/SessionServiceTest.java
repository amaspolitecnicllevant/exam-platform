package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.SessionDto;
import com.examplatform.dto.HistorialDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository      answerRepository;
    @Mock ExamService           examService;
    @Mock com.examplatform.infrastructure.persistence.MatriculaRepository matriculaRepository;
    @Mock CorrectionService     correctionService;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock com.examplatform.infrastructure.persistence.GrupRepository grupRepository;

    SessionService service;

    User student;
    User otherStudent;

    @BeforeEach
    void setUp() {
        service      = new SessionService(sessionRepository, answerRepository, examService, matriculaRepository,
                correctionService, eventPublisher, grupRepository);
        student      = user(Role.STUDENT);
        otherStudent = user(Role.STUDENT);
    }

    // ── startOrResume ─────────────────────────────────────────────────────────

    @Test
    void startOrResume_sessio_creada_per_avancat_inicia_el_rellotge_en_obrir_l_examen() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        ExamSession s = session(exam, student);
        s.setStartedAt(null);   // creada en assignar l'examen al grup
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime abans = LocalDateTime.now();
        service.startOrResume(exam.getId(), student, "10.0.0.5");

        assertThat(s.getStartedAt()).isAfterOrEqualTo(abans);
        assertThat(s.getClientIp()).isEqualTo("10.0.0.5");
        verify(sessionRepository).save(s);
    }

    @Test
    void startOrResume_examen_programat_ja_comencat_compta_des_de_l_hora_programada() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        LocalDateTime programat = LocalDateTime.now().minusMinutes(10);
        exam.setScheduledAt(programat);
        ExamSession s = session(exam, student);
        s.setStartedAt(null);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.startOrResume(exam.getId(), student, "10.0.0.5");

        assertThat(s.getStartedAt()).isEqualTo(programat);
    }

    @Test
    void startOrResume_sessio_ja_comencada_no_reinicia_el_rellotge() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        ExamSession s = session(exam, student);
        LocalDateTime inici = LocalDateTime.now().minusMinutes(20);
        s.setStartedAt(inici);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));

        service.startOrResume(exam.getId(), student, "10.0.0.5");

        assertThat(s.getStartedAt()).isEqualTo(inici);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startOrResume_sessioExistent_retorna_sessio_existent() {
        ExamSession existing = session(exam(ExamStatus.PUBLISHED), student);
        when(sessionRepository.findByExamIdAndStudentId(existing.getExam().getId(), student.getId()))
                .thenReturn(Optional.of(existing));
        when(answerRepository.findBySessionId(existing.getId())).thenReturn(List.of());

        var dto = service.startOrResume(existing.getExam().getId(), student, "127.0.0.1");

        assertThat(dto.id()).isEqualTo(existing.getId());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startOrResume_sessioNova_crea_i_desa() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ExamSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(answerRepository.findBySessionId(any())).thenReturn(List.of());
        // exam sense mòdul: no cal matricula

        service.startOrResume(exam.getId(), student, "127.0.0.1");

        verify(sessionRepository).save(any(ExamSession.class));
    }

    @Test
    void startOrResume_examenNoDraft_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);

        assertThatThrownBy(() -> service.startOrResume(exam.getId(), student, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicat");
    }

    @Test
    void startOrResume_alumneNoMatriculat_llanca_403() {
        Exam exam = examAmModul(ExamStatus.PUBLISHED);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(matriculaRepository.existsByAlumneIdAndModulId(student.getId(), exam.getModul().getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.startOrResume(exam.getId(), student, "127.0.0.1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
    }

    @Test
    void startOrResume_examen_programat_per_a_un_grup_rebutja_alumnes_de_fora_del_grup() {
        Exam exam = examAmModul(ExamStatus.PUBLISHED);
        Grup grup = Grup.builder().id(UUID.randomUUID()).name("Recuperació").build();
        exam.setScheduledGrup(grup);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(matriculaRepository.existsByAlumneIdAndModulId(student.getId(), exam.getModul().getId()))
                .thenReturn(true);
        when(grupRepository.teAlumne(grup.getId(), student.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.startOrResume(exam.getId(), student, "127.0.0.1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Recuperació")
                .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startOrResume_examen_programat_per_a_un_grup_admet_alumnes_del_grup_sense_sessio() {
        Exam exam = examAmModul(ExamStatus.PUBLISHED);
        Grup grup = Grup.builder().id(UUID.randomUUID()).name("Recuperació").build();
        exam.setScheduledGrup(grup);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(matriculaRepository.existsByAlumneIdAndModulId(student.getId(), exam.getModul().getId()))
                .thenReturn(true);
        when(grupRepository.teAlumne(grup.getId(), student.getId())).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ExamSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        lenient().when(answerRepository.findBySessionId(any())).thenReturn(List.of());

        service.startOrResume(exam.getId(), student, "127.0.0.1");

        verify(sessionRepository).save(any(ExamSession.class));
    }

    @Test
    void startOrResume_alumneMatriculat_crea_sessio() {
        Exam exam = examAmModul(ExamStatus.PUBLISHED);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .thenReturn(Optional.empty());
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(matriculaRepository.existsByAlumneIdAndModulId(student.getId(), exam.getModul().getId()))
                .thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ExamSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(answerRepository.findBySessionId(any())).thenReturn(List.of());

        service.startOrResume(exam.getId(), student, "127.0.0.1");

        verify(sessionRepository).save(any(ExamSession.class));
    }

    // ── saveAnswer ────────────────────────────────────────────────────────────

    @Test
    void saveAnswer_creaNouAnswer_si_noExisteix() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.TEXT, new BigDecimal("2"));
        exam.getQuestions().add(q);

        ExamSession s = session(exam, student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId()))
                .thenReturn(Optional.empty());
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveAnswer(s.getId(), new com.examplatform.dto.AnswerDto.SaveRequest(q.getId(), "resposta"), student, "10.0.1.5");

        verify(answerRepository).save(argThat(a -> "resposta".equals(a.getContingut())));
    }

    @Test
    void saveAnswer_examen_tancat_o_desactivat_es_rebutja() {
        for (ExamStatus estat : new ExamStatus[]{ExamStatus.CLOSED, ExamStatus.DRAFT}) {
            Exam exam = exam(estat);
            Question q = question(exam, QuestionType.TEXT, new BigDecimal("2"));
            exam.getQuestions().add(q);
            ExamSession s = session(exam, student);
            when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

            assertThatThrownBy(() -> service.saveAnswer(s.getId(),
                    new com.examplatform.dto.AnswerDto.SaveRequest(q.getId(), "tard"), student, "10.0.1.5"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(estat == ExamStatus.CLOSED ? "tancat" : "desactivat");
        }
        verify(answerRepository, never()).save(any());
    }

    @Test
    void startOrResume_sessio_en_curs_d_un_examen_tancat_es_rebutja() {
        Exam exam = exam(ExamStatus.CLOSED);
        ExamSession s = session(exam, student);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.startOrResume(exam.getId(), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startOrResume_sessio_entregada_d_un_examen_tancat_es_pot_consultar() {
        Exam exam = exam(ExamStatus.CLOSED);
        ExamSession s = session(exam, student);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of());

        assertThat(service.startOrResume(exam.getId(), student, "10.0.1.5").status()).isEqualTo(SessionStatus.SUBMITTED);
    }

    @Test
    void entregaEnCurs_entrega_les_sessions_comencades_i_deixa_les_no_comencades() {
        Exam exam = exam(ExamStatus.CLOSED);
        ExamSession enCurs = session(exam, student);
        ExamSession noComencada = session(exam, otherStudent);
        noComencada.setStartedAt(null);
        ExamSession entregada = session(exam, user(Role.STUDENT));
        entregada.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(List.of(enCurs, noComencada, entregada));

        int n = service.entregaEnCurs(exam.getId());

        assertThat(n).isEqualTo(1);
        assertThat(enCurs.getStatus()).isEqualTo(SessionStatus.SUBMITTED);
        assertThat(enCurs.getSubmittedAt()).isNotNull();
        assertThat(noComencada.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(correctionService).proposaSenseExecucio(enCurs);
        verify(sessionRepository).save(enCurs);
    }

    @Test
    void saveAnswer_sessioEnviada_llanca_excepcio() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.saveAnswer(s.getId(),
                new com.examplatform.dto.AnswerDto.SaveRequest(UUID.randomUUID(), "text"), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveAnswer_alumneAliena_retorna_403() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), otherStudent);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.saveAnswer(s.getId(),
                new com.examplatform.dto.AnswerDto.SaveRequest(UUID.randomUUID(), "text"), student, "10.0.1.5"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private ExamSession sessioAmbAula(String cidr) {
        Exam exam = exam(ExamStatus.PUBLISHED);
        exam.setAula(Aula.builder().id(UUID.randomUUID()).nom("A1").xarxaCidr(cidr).build());
        ExamSession s = session(exam, student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void saveAnswer_des_de_fora_de_l_aula_llanca_403_i_no_desa() {
        ExamSession s = sessioAmbAula("10.0.1.0/24");

        assertThatThrownBy(() -> service.saveAnswer(s.getId(),
                new com.examplatform.dto.AnswerDto.SaveRequest(UUID.randomUUID(), "text"), student, "81.40.1.2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A1");
        verify(answerRepository, never()).save(any());
    }

    @Test
    void saveAnswer_des_de_l_aula_desa() {
        ExamSession s = sessioAmbAula("10.0.1.0/24");
        Question q = question(s.getExam(), QuestionType.TEXT, new BigDecimal("2"));
        s.getExam().getQuestions().add(q);
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId())).thenReturn(Optional.empty());
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveAnswer(s.getId(), new com.examplatform.dto.AnswerDto.SaveRequest(q.getId(), "r"), student, "10.0.1.77");

        verify(answerRepository).save(any());
    }

    @Test
    void saveAnswer_passat_el_temps_i_el_marge_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED);   // 60 min
        ExamSession s = session(exam, student);
        s.setStartedAt(LocalDateTime.now().minusMinutes(62));
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.saveAnswer(s.getId(),
                new com.examplatform.dto.AnswerDto.SaveRequest(UUID.randomUUID(), "tard"), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temps");
        verify(answerRepository, never()).save(any());
    }

    @Test
    void saveAnswer_dins_del_marge_de_gracia_desa() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.TEXT, new BigDecimal("2"));
        exam.getQuestions().add(q);
        ExamSession s = session(exam, student);
        s.setStartedAt(LocalDateTime.now().minusMinutes(60).minusSeconds(30));  // 30 s tard, marge 60 s
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId())).thenReturn(Optional.empty());
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveAnswer(s.getId(), new com.examplatform.dto.AnswerDto.SaveRequest(q.getId(), "r"), student, "10.0.1.5");

        verify(answerRepository).save(any());
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    void submit_marcaSubmitted_i_autocorregeix_choices() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("3"));
        q.setCorrectChoice("b");
        exam.getQuestions().add(q);

        ExamSession s = session(exam, student);
        Answer a = answer(s, q, "b");
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId()))
                .thenReturn(Optional.of(a));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        service.submit(s.getId(), student);

        assertThat(s.getStatus()).isEqualTo(SessionStatus.SUBMITTED);
        assertThat(s.getSubmittedAt()).isNotNull();
        // resposta correcta → punts màxims
        assertThat(a.getAutoScore()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void submit_jaEnviada_llanca_excepcio() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submit(s.getId(), student))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submit_proposa_notes_i_llanca_la_correccio_en_segon_pla() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        ExamSession s = session(exam, student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit(s.getId(), student);

        var ordre = inOrder(correctionService, eventPublisher);
        ordre.verify(correctionService).proposaSenseExecucio(s);
        ordre.verify(eventPublisher).publishEvent(new SessioEntregadaEvent(s.getId()));
    }

    @Test
    void submit_no_retorna_notes_a_l_alumne_si_no_estan_publicades() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("3"));
        q.setCorrectChoice("b");
        exam.getQuestions().add(q);
        ExamSession s = session(exam, student);
        Answer a = answer(s, q, "b");
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId())).thenReturn(Optional.of(a));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        SessionDto dto = service.submit(s.getId(), student);

        assertThat(a.getAutoScore()).isEqualByComparingTo("3");
        assertThat(dto.answers()).singleElement().satisfies(ans -> {
            assertThat(ans.autoScore()).isNull();
            assertThat(ans.manualScore()).isNull();
            assertThat(ans.autoFeedback()).isNull();
        });
    }

    // ── notes visibles per a l'alumne ─────────────────────────────────────────

    private Answer respostaCorregida(ExamSession s) {
        Question q = question(s.getExam(), QuestionType.SHORT, new BigDecimal("2"));
        Answer a = answer(s, q, "resposta");
        a.setAutoScore(new BigDecimal("1"));
        a.setManualScore(new BigDecimal("1.5"));
        a.setAutoFeedback("−1: no esmenta «DHCP»");
        a.setComentari("Bona explicació, però falta el DHCP.");
        return a;
    }

    @Test
    void findById_alumne_sense_notes_publicades_no_rep_puntuacions_ni_motius() {
        ExamSession s = session(exam(ExamStatus.CLOSED), student);
        s.setStatus(SessionStatus.SUBMITTED);
        Answer a = respostaCorregida(s);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        SessionDto dto = service.findById(s.getId(), student);

        assertThat(dto.answers()).singleElement().satisfies(ans -> {
            assertThat(ans.contingut()).isEqualTo("resposta");
            assertThat(ans.autoScore()).isNull();
            assertThat(ans.manualScore()).isNull();
            assertThat(ans.autoFeedback()).isNull();
            assertThat(ans.comentari()).isNull();
        });
    }

    @Test
    void findById_alumne_amb_notes_publicades_rep_puntuacions_i_motius() {
        Exam exam = exam(ExamStatus.CLOSED);
        exam.setNotesVisibles(true);
        ExamSession s = session(exam, student);
        Answer a = respostaCorregida(s);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        SessionDto dto = service.findById(s.getId(), student);

        assertThat(dto.answers()).singleElement().satisfies(ans -> {
            assertThat(ans.manualScore()).isEqualByComparingTo("1.5");
            assertThat(ans.autoFeedback()).contains("DHCP");
            assertThat(ans.comentari()).isEqualTo("Bona explicació, però falta el DHCP.");
        });
    }

    @Test
    void findById_professor_sempre_rep_puntuacions() {
        ExamSession s = session(exam(ExamStatus.CLOSED), student);
        Answer a = respostaCorregida(s);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        SessionDto dto = service.findById(s.getId(), user(Role.PROFESSOR));

        assertThat(dto.answers()).singleElement().satisfies(ans -> {
            assertThat(ans.autoScore()).isEqualByComparingTo("1");
            assertThat(ans.autoFeedback()).isNotNull();
        });
    }

    // ── accés del professor a sessions ────────────────────────────────────────

    @Test
    void findByExam_comprova_que_el_professor_pot_gestionar_l_examen() {
        Exam exam = exam(ExamStatus.CLOSED);
        User prof = user(Role.PROFESSOR);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(List.of());

        service.findByExam(exam.getId(), prof);

        verify(examService).assertOwnership(exam, prof);
    }

    @Test
    void findByExam_d_un_examen_alie_llanca_AccessDenied_i_no_retorna_res() {
        Exam exam = exam(ExamStatus.CLOSED);
        User prof = user(Role.PROFESSOR);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                .when(examService).assertOwnership(exam, prof);

        assertThatThrownBy(() -> service.findByExam(exam.getId(), prof))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(sessionRepository, never()).findByExamIdWithDetails(any());
    }

    @Test
    void findById_professor_d_un_examen_alie_llanca_AccessDenied() {
        ExamSession s = session(exam(ExamStatus.CLOSED), student);
        User prof = user(Role.PROFESSOR);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                .when(examService).assertOwnership(s.getExam(), prof);

        assertThatThrownBy(() -> service.findById(s.getId(), prof))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(answerRepository, never()).findBySessionId(any());
    }

    @Test
    void monitor_usa_la_mateixa_regla_que_modificar_l_examen() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        User profDelModul = user(Role.PROFESSOR);   // no n'és el creador
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(List.of());

        assertThat(service.monitor(exam.getId(), profDelModul)).isEmpty();
        verify(examService).assertOwnership(exam, profDelModul);
    }

    // ── tornar a fer (alumne) ─────────────────────────────────────────────────

    private ExamSession entregada(Exam exam, int minutsDesDeInici) {
        ExamSession s = session(exam, student);
        s.setStatus(SessionStatus.SUBMITTED);
        s.setSubmittedAt(LocalDateTime.now());
        s.setStartedAt(LocalDateTime.now().minusMinutes(minutsDesDeInici));
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void restart_dins_del_temps_esborra_respostes_i_no_reinicia_el_rellotge() {
        Exam exam = exam(ExamStatus.PUBLISHED);   // 60 min
        ExamSession s = entregada(exam, 20);
        LocalDateTime inici = s.getStartedAt();
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.restartByStudent(exam.getId(), student, "10.0.1.5");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(s.getStartedAt()).isEqualTo(inici);
        verify(answerRepository).deleteBySessionId(s.getId());
    }

    @Test
    void restart_amb_l_examen_tancat_no_esborra_res() {
        Exam exam = exam(ExamStatus.CLOSED);
        ExamSession s = entregada(exam, 20);

        assertThatThrownBy(() -> service.restartByStudent(exam.getId(), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tancat");
        verify(answerRepository, never()).deleteBySessionId(any());
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SUBMITTED);
    }

    @Test
    void restart_amb_notes_publicades_no_esborra_res() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        exam.setNotesVisibles(true);
        entregada(exam, 20);

        assertThatThrownBy(() -> service.restartByStudent(exam.getId(), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("notes");
        verify(answerRepository, never()).deleteBySessionId(any());
    }

    @Test
    void restart_amb_el_temps_esgotat_no_dona_mes_temps() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        entregada(exam, 61);

        assertThatThrownBy(() -> service.restartByStudent(exam.getId(), student, "10.0.1.5"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("temps");
        verify(answerRepository, never()).deleteBySessionId(any());
    }

    @Test
    void restart_des_de_fora_de_l_aula_no_esborra_res() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        exam.setAula(Aula.builder().id(UUID.randomUUID()).nom("A1").xarxaCidr("10.0.1.0/24").build());
        entregada(exam, 20);

        assertThatThrownBy(() -> service.restartByStudent(exam.getId(), student, "81.40.1.2"))
                .isInstanceOf(ResponseStatusException.class);
        verify(answerRepository, never()).deleteBySessionId(any());
    }

    // ── reprendre sessió (professor) ──────────────────────────────────────────

    private ExamSession entregadaAmb(int minutsUsats, int minutsDesDeLEntrega) {
        Exam exam = exam(ExamStatus.PUBLISHED);   // 60 min
        ExamSession s = session(exam, student);
        LocalDateTime entrega = LocalDateTime.now().minusMinutes(minutsDesDeLEntrega);
        s.setStartedAt(entrega.minusMinutes(minutsUsats));
        s.setSubmittedAt(entrega);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    private static long minutsRestants(ExamSession s) {
        return java.time.Duration.between(LocalDateTime.now(),
                s.getStartedAt().plusMinutes(s.getExam().getDurada())).toMinutes();
    }

    @Test
    void reprendre_conserva_el_temps_que_quedava_en_entregar() {
        ExamSession s = entregadaAmb(30, 45);   // va usar 30 min, fa 45 min que va entregar

        service.resetSession(s.getId(), user(Role.PROFESSOR));

        assertThat(s.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(minutsRestants(s)).isBetween(29L, 30L);
    }

    @Test
    void reprendre_amb_el_temps_esgotat_dona_el_minim() {
        ExamSession s = entregadaAmb(60, 5);

        service.resetSession(s.getId(), user(Role.PROFESSOR));

        assertThat(minutsRestants(s)).isBetween(9L, 10L);
    }

    @Test
    void reprendre_en_un_examen_tancat_es_rebutja() {
        ExamSession s = entregadaAmb(30, 45);
        s.getExam().setStatus(ExamStatus.CLOSED);
        reset(sessionRepository);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.resetSession(s.getId(), user(Role.PROFESSOR)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SUBMITTED);
    }

    @Test
    void reprendre_no_la_torna_a_entregar_el_planificador() {
        ExamSession s = entregadaAmb(60, 120);

        service.resetSession(s.getId(), user(Role.PROFESSOR));

        // findExpiredInProgress: started_at + durada < ara
        assertThat(s.getStartedAt().plusMinutes(s.getExam().getDurada())).isAfter(LocalDateTime.now());
    }

    // ── reCorregeixTest ───────────────────────────────────────────────────────

    @Test
    void reCorregeixTest_aplica_la_nova_penalitzacio_a_les_respostes_ja_entregades() {
        Exam exam = exam(ExamStatus.CLOSED);
        exam.setPenalitzacioChoice(new BigDecimal("0.25"));   // la nova
        Question test = question(exam, QuestionType.CHOICE, new BigDecimal("2"));
        test.setCorrectChoice("b");
        Question text = question(exam, QuestionType.TEXT, new BigDecimal("3"));
        exam.getQuestions().addAll(List.of(test, text));
        ExamSession s = session(exam, student);
        Answer errada = answer(s, test, "a");
        errada.setAutoScore(BigDecimal.ZERO);                 // corregida sense penalització
        Answer encert = answer(s, test, "b");
        encert.setAutoScore(new BigDecimal("2"));
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(answerRepository.findByQuestionId(test.getId())).thenReturn(List.of(errada, encert));

        int n = service.reCorregeixTest(exam.getId());

        assertThat(n).isEqualTo(2);
        assertThat(errada.getAutoScore()).isEqualByComparingTo("-0.5");
        assertThat(encert.getAutoScore()).isEqualByComparingTo("2");
        verify(answerRepository, never()).findByQuestionId(text.getId());
    }

    // ── historial de l'alumne ─────────────────────────────────────────────────

    private ExamSession entregadaEl(Exam exam, LocalDateTime quan) {
        ExamSession s = session(exam, student);
        s.setStatus(SessionStatus.SUBMITTED);
        s.setSubmittedAt(quan);
        return s;
    }

    @Test
    void historial_nomes_mostra_la_nota_si_s_ha_publicat() {
        Exam publicat = exam(ExamStatus.CLOSED);
        publicat.setNotesVisibles(true);
        Question q1 = question(publicat, QuestionType.SHORT, new BigDecimal("6"));
        Question q2 = question(publicat, QuestionType.CHOICE, new BigDecimal("4"));
        publicat.getQuestions().addAll(List.of(q1, q2));
        Exam ocult = exam(ExamStatus.CLOSED);
        ocult.getQuestions().add(question(ocult, QuestionType.SHORT, new BigDecimal("10")));

        ExamSession sPublicat = entregadaEl(publicat, LocalDateTime.now().minusDays(3));
        ExamSession sOcult = entregadaEl(ocult, LocalDateTime.now().minusDays(1));
        Answer a1 = answer(sPublicat, q1, "resposta");
        a1.setAutoScore(new BigDecimal("4"));
        a1.setManualScore(new BigDecimal("5.5"));   // compta la revisada, una sola vegada
        Answer a2 = answer(sPublicat, q2, "b");
        a2.setAutoScore(new BigDecimal("4"));
        when(sessionRepository.findByStudentIdWithExam(student.getId())).thenReturn(List.of(sOcult, sPublicat));
        when(answerRepository.findBySessionIdIn(List.of(sPublicat.getId()))).thenReturn(List.of(a1, a2));

        var h = service.historial(student);

        assertThat(h).extracting(HistorialDto::sessionId).containsExactly(sPublicat.getId(), sOcult.getId());
        assertThat(h.get(0).nota()).isEqualByComparingTo("9.5");
        assertThat(h.get(0).notesVisibles()).isTrue();
        assertThat(h.get(1).nota()).isNull();
        assertThat(h.get(1).notesVisibles()).isFalse();
    }

    @Test
    void historial_no_inclou_sessions_no_entregades() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        ExamSession enCurs = session(exam, student);
        when(sessionRepository.findByStudentIdWithExam(student.getId())).thenReturn(List.of(enCurs));

        assertThat(service.historial(student)).isEmpty();
        verify(answerRepository, never()).findBySessionIdIn(any());
    }

    @Test
    void historial_aplica_el_bonus_encara_que_no_respongues() {
        Exam exam = exam(ExamStatus.CLOSED);
        exam.setNotesVisibles(true);
        Question bonus = question(exam, QuestionType.CHOICE, new BigDecimal("2"));
        bonus.setAnulada(true);
        exam.getQuestions().add(bonus);
        ExamSession s = entregadaEl(exam, LocalDateTime.now());
        when(sessionRepository.findByStudentIdWithExam(student.getId())).thenReturn(List.of(s));
        when(answerRepository.findBySessionIdIn(List.of(s.getId()))).thenReturn(List.of());

        // L'examen només té la pregunta de bonus (2 de 2 punts): un 10
        assertThat(service.historial(student).get(0).nota()).isEqualByComparingTo("10");
    }

    @Test
    void historial_dona_la_nota_sobre_10_encara_que_l_examen_valgui_20_punts() {
        Exam exam = exam(ExamStatus.CLOSED);
        exam.setNotesVisibles(true);
        Question q = question(exam, QuestionType.SHORT, new BigDecimal("20"));
        exam.getQuestions().add(q);
        ExamSession s = entregadaEl(exam, LocalDateTime.now());
        Answer a = answer(s, q, "resposta");
        a.setManualScore(new BigDecimal("13"));
        when(sessionRepository.findByStudentIdWithExam(student.getId())).thenReturn(List.of(s));
        when(answerRepository.findBySessionIdIn(List.of(s.getId()))).thenReturn(List.of(a));

        assertThat(service.historial(student).get(0).nota()).isEqualByComparingTo("6.5");
    }

    @Test
    void historial_inclou_el_modul() {
        Exam exam = examAmModul(ExamStatus.CLOSED);
        ExamSession s = entregadaEl(exam, LocalDateTime.now());
        when(sessionRepository.findByStudentIdWithExam(student.getId())).thenReturn(List.of(s));

        var h = service.historial(student).get(0);

        assertThat(h.modulNom()).isEqualTo("SI");
        assertThat(h.modulId()).isEqualTo(exam.getModul().getId());
    }

    // ── forceSubmit ───────────────────────────────────────────────────────────

    @Test
    void forceSubmit_envia_sense_comprovacio_de_propietat() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        ExamSession s = session(exam, student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.forceSubmit(s.getId());

        assertThat(s.getStatus()).isEqualTo(SessionStatus.SUBMITTED);
        verify(correctionService).proposaSenseExecucio(s);
        verify(eventPublisher).publishEvent(new SessioEntregadaEvent(s.getId()));
    }

    @Test
    void forceSubmit_jaEnviada_es_idempotent() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        service.forceSubmit(s.getId());

        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(correctionService, eventPublisher);
    }

    // ── recordFocusLoss ───────────────────────────────────────────────────────

    @Test
    void recordFocusLoss_incrementaComptador() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordFocusLoss(s.getId(), student);

        assertThat(s.getFocusLossCount()).isEqualTo(1);
    }

    @Test
    void recordFocusLoss_sessioEnviada_noFaRes() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        s.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        service.recordFocusLoss(s.getId(), student);

        verify(sessionRepository, never()).save(any());
        assertThat(s.getFocusLossCount()).isZero();
    }

    // ── autoCorrectChoices via submit ─────────────────────────────────────────

    @Test
    void autoCorrect_choice_incorrecte_amb_factor_penalitza() {
        Exam exam = examAmPenalitzacio(new BigDecimal("0.33"));
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("3"));
        q.setCorrectChoice("a");
        exam.getQuestions().add(q);

        ExamSession s = session(exam, student);
        Answer a = answer(s, q, "b");  // incorrecte
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId()))
                .thenReturn(Optional.of(a));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        service.submit(s.getId(), student);

        // -3 × 0.33 = -0.99
        assertThat(a.getAutoScore()).isEqualByComparingTo(new BigDecimal("-0.9900"));
    }

    @Test
    void autoCorrect_choice_buit_zero_sense_penalitzar() {
        Exam exam = examAmPenalitzacio(new BigDecimal("0.33"));
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("3"));
        q.setCorrectChoice("a");
        exam.getQuestions().add(q);

        ExamSession s = session(exam, student);
        Answer a = answer(s, q, "");  // buit
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionIdAndQuestionId(s.getId(), q.getId()))
                .thenReturn(Optional.of(a));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of(a));

        service.submit(s.getId(), student);

        assertThat(a.getAutoScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── reCorrectQuestion ─────────────────────────────────────────────────────

    @Test
    void reCorrectQuestion_choice_recalcula_scores() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("5"));
        q.setCorrectChoice("c");

        ExamSession s = session(exam, student);
        Answer a = answer(s, q, "c");  // correcta
        when(answerRepository.findByQuestionId(q.getId())).thenReturn(List.of(a));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reCorrectQuestion(q);

        assertThat(a.getAutoScore()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void reCorrectQuestion_bonus_no_sobreescriu_les_notes_de_test() {
        // El bonus s'aplica en calcular la nota (Puntuacio): les notes guardades es conserven
        // perquè treure el bonus les recuperi
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.CHOICE, new BigDecimal("2"));
        q.setCorrectChoice("a");
        q.setAnulada(true);
        Answer aCorrecta   = answer(session(exam, student),      q, "a");
        Answer aIncorrecta = answer(session(exam, otherStudent),  q, "d");
        when(answerRepository.findByQuestionId(q.getId())).thenReturn(List.of(aCorrecta, aIncorrecta));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int n = service.reCorrectQuestion(q);

        assertThat(n).isEqualTo(2);
        assertThat(aCorrecta.getAutoScore()).isEqualByComparingTo("2");
        assertThat(aIncorrecta.getAutoScore()).isEqualByComparingTo("0");
        assertThat(Puntuacio.punts(q, aIncorrecta)).isEqualByComparingTo("2");
    }

    @Test
    void reCorrectQuestion_pregunta_de_text_no_es_toca() {
        Exam exam = exam(ExamStatus.PUBLISHED);
        Question q = question(exam, QuestionType.SHORT, new BigDecimal("2"));
        q.setAnulada(true);

        assertThat(service.reCorrectQuestion(q)).isZero();
        verifyNoInteractions(answerRepository);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_alumne_pot_veure_propia_sessio() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), student);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.findById(s.getId(), student));
    }

    @Test
    void findById_alumne_no_pot_veure_sessio_aliena_retorna_403() {
        ExamSession s = session(exam(ExamStatus.PUBLISHED), otherStudent);
        when(sessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.findById(s.getId(), student))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).role(role)
                .email(UUID.randomUUID() + "@test.cat").name("Test").build();
    }

    private Exam exam(ExamStatus status) {
        Exam e = Exam.builder().id(UUID.randomUUID()).title("Examen test")
                .durada(60).status(status).createdBy(user(Role.PROFESSOR))
                .penalitzacioChoice(BigDecimal.ZERO).build();
        return e;
    }

    private Exam examAmModul(ExamStatus status) {
        com.examplatform.domain.model.Modul modul =
                com.examplatform.domain.model.Modul.builder().id(UUID.randomUUID())
                        .codi("0483").nom("SI").build();
        Exam e = exam(status);
        e.setModul(modul);
        return e;
    }

    private Exam examAmPenalitzacio(BigDecimal factor) {
        Exam e = exam(ExamStatus.PUBLISHED);
        e.setPenalitzacioChoice(factor);
        return e;
    }

    private Question question(Exam exam, QuestionType tipus, BigDecimal punts) {
        return Question.builder().id(UUID.randomUUID()).exam(exam)
                .tipus(tipus).punts(punts).enunciat("Pregunta test").ordre(1).build();
    }

    private ExamSession session(Exam exam, User student) {
        return ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(student)
                .startedAt(LocalDateTime.now()).build();
    }

    private Answer answer(ExamSession session, Question question, String contingut) {
        return Answer.builder().id(UUID.randomUUID())
                .session(session).question(question).contingut(contingut).build();
    }
}
