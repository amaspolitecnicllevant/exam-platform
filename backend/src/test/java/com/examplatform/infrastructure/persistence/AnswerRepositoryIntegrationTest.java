package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.CorrectionService;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class AnswerRepositoryIntegrationTest {

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
    Exam exam;
    Question curta;
    Question choice;
    Question codi;
    Question anulada;
    int alumnes;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        sessionRepository.deleteAll();
        examRepository.deleteAll();
        userRepository.deleteAll();

        professor = userRepository.save(User.builder()
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());
        exam = Exam.builder().rawMd("# Test").rawMd("# Test")
                .title("Test").durada(60).status(ExamStatus.CLOSED)
                .createdBy(professor).penalitzacioChoice(BigDecimal.ZERO)
                .questions(new ArrayList<>()).build();
        curta = question(1, QuestionType.SHORT);
        curta.setClaus("DHCP");
        choice = question(2, QuestionType.CHOICE);
        codi = question(3, QuestionType.BASH_CMD);
        anulada = question(4, QuestionType.LONG);
        anulada.setAnulada(true);
        exam = examRepository.save(exam);
        curta = exam.getQuestions().get(0);
        choice = exam.getQuestions().get(1);
        codi = exam.getQuestions().get(2);
        anulada = exam.getQuestions().get(3);
    }

    private Question question(int ordre, QuestionType tipus) {
        Question q = Question.builder().exam(exam).ordre(ordre).tipus(tipus)
                .enunciat("P" + ordre).punts(new BigDecimal("2.5")).build();
        exam.getQuestions().add(q);
        return q;
    }

    private ExamSession session(SessionStatus status) {
        User student = userRepository.save(User.builder()
                .name("Alumne").email("alumne" + (++alumnes) + "@test.cat").role(Role.STUDENT).build());
        return sessionRepository.save(ExamSession.builder()
                .exam(exam).student(student).status(status).build());
    }

    private Answer answer(ExamSession s, Question q, String contingut, String manualScore) {
        return answerRepository.save(Answer.builder().session(s).question(q).contingut(contingut)
                .manualScore(manualScore == null ? null : new BigDecimal(manualScore)).build());
    }

    // ── findPendentsRevisio ──────────────────────────────────────────────────

    @Test
    void pendents_son_les_no_revisades_de_sessions_entregades() {
        ExamSession entregada = session(SessionStatus.SUBMITTED);
        Answer pendentCurta = answer(entregada, curta, "DHCP", null);
        Answer pendentCodi = answer(entregada, codi, "ls", null);
        answer(entregada, choice, "a", null);          // CHOICE: no cal revisar
        answer(entregada, anulada, "text", null);      // anul·lada: no cal revisar

        ExamSession revisada = session(SessionStatus.SUBMITTED);
        answer(revisada, curta, "DHCP", "2");          // ja té nota manual

        ExamSession enCurs = session(SessionStatus.IN_PROGRESS);
        answer(enCurs, curta, "DHCP", null);           // encara no entregada

        List<Answer> pendents = answerRepository.findPendentsRevisio(
                exam.getId(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS);

        assertThat(pendents).extracting(Answer::getId)
                .containsExactlyInAnyOrder(pendentCurta.getId(), pendentCodi.getId());
    }

    @Test
    void pendents_d_un_altre_examen_no_compten() {
        answer(session(SessionStatus.SUBMITTED), curta, "DHCP", null);

        assertThat(answerRepository.findPendentsRevisio(
                java.util.UUID.randomUUID(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS)).isEmpty();
    }

    // ── findIdsAmbContingut ──────────────────────────────────────────────────

    @Test
    void ids_amb_contingut_filtra_per_sessio_tipus_i_contingut() {
        ExamSession s = session(SessionStatus.SUBMITTED);
        Answer ambCodi = answer(s, codi, "ls -la", null);
        answer(s, curta, "text", null);
        ExamSession altra = session(SessionStatus.SUBMITTED);
        answer(altra, codi, "pwd", null);

        List<java.util.UUID> ids = answerRepository.findIdsAmbContingut(
                s.getId(), List.of(QuestionType.BASH_CMD, QuestionType.JAVA_PROG));

        assertThat(ids).containsExactly(ambCodi.getId());
    }

    @Test
    void ids_amb_contingut_exclou_respostes_null() {
        ExamSession s = session(SessionStatus.SUBMITTED);
        answer(s, codi, null, null);

        assertThat(answerRepository.findIdsAmbContingut(s.getId(), List.of(QuestionType.BASH_CMD))).isEmpty();
    }

    // ── migració V25 ─────────────────────────────────────────────────────────

    @Test
    void desa_i_llegeix_claus_i_auto_feedback() {
        ExamSession s = session(SessionStatus.SUBMITTED);
        Answer a = answer(s, curta, "res", null);
        a.setAutoFeedback("−2,5: no esmenta «DHCP»");
        a.setComentari("Revisa el protocol.");
        answerRepository.saveAndFlush(a);

        Answer llegida = answerRepository.findById(a.getId()).orElseThrow();
        assertThat(llegida.getAutoFeedback()).isEqualTo("−2,5: no esmenta «DHCP»");
        assertThat(llegida.getComentari()).isEqualTo("Revisa el protocol.");
        assertThat(llegida.getQuestion().getClaus()).isEqualTo("DHCP");
    }
}
