package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class SessionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ExamSessionRepository sessionRepository;
    @Autowired ExamRepository        examRepository;
    @Autowired UserRepository        userRepository;
    @Autowired AnswerRepository      answerRepository;

    User professor;
    User student;
    Exam exam;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        sessionRepository.deleteAll();
        examRepository.deleteAll();
        userRepository.deleteAll();

        professor = userRepository.save(User.builder()
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());
        student = userRepository.save(User.builder()
                .name("Alumne").email("alumne@test.cat").role(Role.STUDENT).build());
        exam = examRepository.save(Exam.builder().rawMd("# Test").rawMd("# Test")
                .title("Test").durada(60).status(ExamStatus.PUBLISHED)
                .createdBy(professor).penalitzacioChoice(BigDecimal.ZERO)
                .questions(new ArrayList<>()).build());
    }

    // ── findByExamIdWithDetails ───────────────────────────────────────────────

    @Test
    void findByExamIdWithDetails_carrega_student_i_exam_sense_N_plus_1() {
        ExamSession session = sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student).build());

        List<ExamSession> result = sessionRepository.findByExamIdWithDetails(exam.getId());

        assertThat(result).hasSize(1);
        ExamSession loaded = result.get(0);
        assertThat(loaded.getStudent().getName()).isEqualTo("Alumne");
        assertThat(loaded.getExam().getTitle()).isEqualTo("Test");
        assertThat(loaded.getId()).isEqualTo(session.getId());
    }

    @Test
    void findByExamIdWithDetails_retorna_buit_si_no_hi_ha_sessions() {
        assertThat(sessionRepository.findByExamIdWithDetails(exam.getId())).isEmpty();
    }

    @Test
    void findByExamIdWithDetails_no_retorna_sessions_d_altres_examens() {
        Exam altreExam = examRepository.save(Exam.builder().rawMd("# Test").rawMd("# Test")
                .title("Altre").durada(30).status(ExamStatus.PUBLISHED)
                .createdBy(professor).penalitzacioChoice(BigDecimal.ZERO)
                .questions(new ArrayList<>()).build());
        sessionRepository.save(ExamSession.builder().exam(altreExam).student(student).build());

        assertThat(sessionRepository.findByExamIdWithDetails(exam.getId())).isEmpty();
    }

    // ── findByExamIdAndStudentId ──────────────────────────────────────────────

    @Test
    void findByExamIdAndStudentId_troba_sessio_existent() {
        ExamSession session = sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student).build());

        assertThat(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .isPresent().get().extracting(ExamSession::getId).isEqualTo(session.getId());
    }

    @Test
    void findByExamIdAndStudentId_retorna_buit_si_no_existeix() {
        assertThat(sessionRepository.findByExamIdAndStudentId(exam.getId(), student.getId()))
                .isEmpty();
    }

    // ── findExpiredInProgress ─────────────────────────────────────────────────

    @Test
    void findExpiredInProgress_retorna_sessio_expirada() {
        ExamSession session = sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student)
                .startedAt(LocalDateTime.now().minusMinutes(exam.getDurada() + 5))
                .build());

        List<ExamSession> expired = sessionRepository.findExpiredInProgress();

        assertThat(expired).extracting(ExamSession::getId).contains(session.getId());
    }

    @Test
    void findExpiredInProgress_no_retorna_sessio_recent() {
        sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student)
                .startedAt(LocalDateTime.now())
                .build());

        assertThat(sessionRepository.findExpiredInProgress()).isEmpty();
    }

    @Test
    void findExpiredInProgress_no_retorna_sessio_ja_enviada() {
        sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student)
                .startedAt(LocalDateTime.now().minusMinutes(exam.getDurada() + 5))
                .status(SessionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusMinutes(1))
                .build());

        assertThat(sessionRepository.findExpiredInProgress()).isEmpty();
    }

    @Test
    void reiniciar_startedAt_es_desa_i_la_sessio_deixa_d_estar_expirada() {
        ExamSession session = sessionRepository.saveAndFlush(ExamSession.builder()
                .exam(exam).student(student)
                .startedAt(LocalDateTime.now().minusMinutes(exam.getDurada() + 5))
                .build());
        assertThat(sessionRepository.findExpiredInProgress()).extracting(ExamSession::getId)
                .contains(session.getId());

        // com fa SessionService.restartByStudent
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.saveAndFlush(session);

        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStartedAt())
                .isAfter(LocalDateTime.now().minusMinutes(1));
        assertThat(sessionRepository.findExpiredInProgress()).extracting(ExamSession::getId)
                .doesNotContain(session.getId());
    }

    @Test
    void findExpiredInProgress_amb_marge_espera_els_segons_indicats() {
        ExamSession s = sessionRepository.save(ExamSession.builder().exam(exam).student(student)
                .startedAt(LocalDateTime.now().minusMinutes(exam.getDurada()).minusSeconds(10)).build());

        assertThat(sessionRepository.findExpiredInProgress(0)).extracting(ExamSession::getId).contains(s.getId());
        assertThat(sessionRepository.findExpiredInProgress(60)).extracting(ExamSession::getId).doesNotContain(s.getId());
    }

    @Test
    void findExpiredInProgress_ignora_sessions_que_encara_no_han_comencat() {
        sessionRepository.saveAndFlush(ExamSession.builder()
                .exam(exam).student(student).startedAt(null).build());

        assertThat(sessionRepository.findExpiredInProgress()).isEmpty();
    }

    // ── findStudentIdsByExamId ────────────────────────────────────────────────

    @Test
    void findStudentIdsByExamId_retorna_ids_dels_alumnes() {
        User student2 = userRepository.save(User.builder()
                .name("Alumne2").email("alumne2@test.cat").role(Role.STUDENT).build());
        sessionRepository.save(ExamSession.builder().exam(exam).student(student).build());
        sessionRepository.save(ExamSession.builder().exam(exam).student(student2).build());

        List<UUID> ids = sessionRepository.findStudentIdsByExamId(exam.getId())
                .stream().map(o -> (UUID) o).toList();

        assertThat(ids).hasSize(2)
                .containsExactlyInAnyOrder(student.getId(), student2.getId());
    }
}
