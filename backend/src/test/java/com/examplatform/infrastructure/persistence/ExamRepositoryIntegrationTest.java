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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class ExamRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ExamRepository        examRepository;
    @Autowired ExamSessionRepository sessionRepository;
    @Autowired UserRepository        userRepository;
    @Autowired GrupRepository        grupRepository;

    User professor;
    User student;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        examRepository.deleteAll();
        grupRepository.deleteAll();
        userRepository.deleteAll();

        professor = userRepository.save(User.builder()
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());
        student = userRepository.save(User.builder()
                .name("Alumne").email("alumne@test.cat").role(Role.STUDENT).build());
    }

    // ── findPublishedForStudent ───────────────────────────────────────────────

    @Test
    void findPublishedForStudent_retorna_examen_publicat_amb_sessio() {
        Exam exam = examRepository.save(examPublicat());
        sessionRepository.save(ExamSession.builder().exam(exam).student(student).build());

        List<Exam> result = examRepository.findPublishedForStudent(student.getId());

        assertThat(result).hasSize(1).first().extracting(Exam::getId).isEqualTo(exam.getId());
    }

    @Test
    void findPublishedForStudent_no_retorna_examen_sense_sessio() {
        examRepository.save(examPublicat());

        assertThat(examRepository.findPublishedForStudent(student.getId())).isEmpty();
    }

    @Test
    void findPublishedForStudent_no_retorna_examen_tancat() {
        Exam exam = examRepository.save(exam(ExamStatus.CLOSED));
        sessionRepository.save(ExamSession.builder().exam(exam).student(student).build());

        assertThat(examRepository.findPublishedForStudent(student.getId())).isEmpty();
    }

    @Test
    void findPublishedForStudent_no_retorna_sessions_d_altres_alumnes() {
        User altreAlumne = userRepository.save(User.builder()
                .name("Altre").email("altre@test.cat").role(Role.STUDENT).build());

        Exam exam = examRepository.save(examPublicat());
        sessionRepository.save(ExamSession.builder().exam(exam).student(altreAlumne).build());

        assertThat(examRepository.findPublishedForStudent(student.getId())).isEmpty();
    }

    // ── findScheduledForGrup ─────────────────────────────────────────────────

    @Test
    void findScheduledForGrup_retorna_examen_programat_al_grup() {
        Grup grup = grupRepository.save(Grup.builder().name("1r DAW").createdBy(professor).build());
        Exam exam = examRepository.save(examProgramat(grup));

        List<Exam> result = examRepository.findScheduledForGrup(grup.getId(), java.util.UUID.randomUUID());

        assertThat(result).hasSize(1).first().extracting(Exam::getId).isEqualTo(exam.getId());
    }

    @Test
    void findScheduledForGrup_exclou_el_mateix_examen() {
        Grup grup = grupRepository.save(Grup.builder().name("1r SMX").createdBy(professor).build());
        Exam exam = examRepository.save(examProgramat(grup));

        assertThat(examRepository.findScheduledForGrup(grup.getId(), exam.getId())).isEmpty();
    }

    @Test
    void findScheduledForGrup_no_retorna_examen_tancat() {
        Grup grup = grupRepository.save(Grup.builder().name("2n DAW").createdBy(professor).build());
        Exam exam = examRepository.save(examProgramat(grup));
        exam.setStatus(ExamStatus.CLOSED);
        examRepository.save(exam);

        assertThat(examRepository.findScheduledForGrup(grup.getId(), java.util.UUID.randomUUID())).isEmpty();
    }

    // ── findDueForActivation ─────────────────────────────────────────────────

    @Test
    void findDueForActivation_retorna_examen_que_toca_activar() {
        Grup grup = grupRepository.save(Grup.builder().name("3r ASIX").createdBy(professor).build());
        Exam exam = examRepository.save(examProgramat(grup));
        exam.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        examRepository.save(exam);

        assertThat(examRepository.findDueForActivation(LocalDateTime.now()))
                .hasSize(1).first().extracting(Exam::getId).isEqualTo(exam.getId());
    }

    @Test
    void findDueForActivation_no_retorna_examen_futur() {
        Grup grup = grupRepository.save(Grup.builder().name("3r SMX").createdBy(professor).build());
        Exam exam = examRepository.save(examProgramat(grup));
        exam.setScheduledAt(LocalDateTime.now().plusHours(1));
        examRepository.save(exam);

        assertThat(examRepository.findDueForActivation(LocalDateTime.now())).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Exam examPublicat() {
        return exam(ExamStatus.PUBLISHED);
    }

    private Exam exam(ExamStatus status) {
        return Exam.builder().rawMd("# Test").rawMd("# Test")
                .title("Test").durada(60).status(status)
                .createdBy(professor).penalitzacioChoice(BigDecimal.ZERO)
                .questions(new ArrayList<>()).build();
    }

    private Exam examProgramat(Grup grup) {
        Exam e = exam(ExamStatus.DRAFT);
        e.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        e.setScheduledGrup(grup);
        return e;
    }

    @Test
    void teAlumne_nomes_per_als_alumnes_del_grup() {
        User altre = userRepository.save(User.builder()
                .name("Altre").email("altre@test.cat").role(Role.STUDENT).build());
        Grup grup = grupRepository.save(Grup.builder().name("Recuperació").createdBy(professor)
                .students(new java.util.HashSet<>(java.util.Set.of(student))).build());

        assertThat(grupRepository.teAlumne(grup.getId(), student.getId())).isTrue();
        assertThat(grupRepository.teAlumne(grup.getId(), altre.getId())).isFalse();
    }

    @Test
    void els_correus_es_desen_en_minuscules_i_es_cerquen_sense_distingir_majuscules() {
        User u = userRepository.save(User.builder().name("Anna").email(" Anna.Puig@Test.CAT ").role(Role.STUDENT).build());

        assertThat(u.getEmail()).isEqualTo("anna.puig@test.cat");
        assertThat(userRepository.findByEmail("ANNA.PUIG@test.cat ")).map(User::getId).contains(u.getId());
        assertThat(userRepository.existsByEmail("Anna.Puig@Test.Cat")).isTrue();
        assertThat(userRepository.existsByEmail("altra@test.cat")).isFalse();
    }
}
