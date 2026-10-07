package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.EditorPreguntesService;
import com.examplatform.domain.service.ExamService;
import com.examplatform.dto.QuestionEditRequest;
import com.examplatform.infrastructure.parser.MarkdownExamParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** L'editor d'exàmens contra PostgreSQL real: renumeració amb la restricció única (examen, ordre). */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class EditorPreguntesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestEntityManager em;
    @Autowired UserRepository userRepository;
    @Autowired ExamRepository examRepository;
    @Autowired QuestionRepository questionRepository;
    @Autowired QuestionFileRepository fileRepository;
    @Autowired ExamSessionRepository sessionRepository;

    User prof;
    Exam exam;
    EditorPreguntesService service;

    private void prepara() {
        prof = userRepository.save(User.builder().name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());
        exam = Exam.builder().title("Examen").durada(60).createdBy(prof).rawMd("x").questions(new ArrayList<>()).build();
        for (int i = 1; i <= 3; i++) {
            exam.getQuestions().add(Question.builder().exam(exam).ordre(i).tipus(QuestionType.SHORT)
                    .enunciat("P" + i).punts(new BigDecimal("3")).build());
        }
        exam = examRepository.saveAndFlush(exam);
        ExamService examService = mock(ExamService.class);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        service = new EditorPreguntesService(examService, new MarkdownExamParser(), questionRepository,
                fileRepository, sessionRepository);
        ReflectionTestUtils.setField(service, "imatgeMaxBytes", 5L * 1024 * 1024);
    }

    private static QuestionEditRequest curta(String enunciat, Integer posicio) {
        return new QuestionEditRequest(QuestionType.SHORT, enunciat, new BigDecimal("1"), null, null, null,
                null, null, null, null, null, null, null, null, null, null, posicio);
    }

    private List<String> enunciats() {
        em.flush();
        em.clear();
        return questionRepository.findByExamIdOrderByOrdreAsc(exam.getId()).stream()
                .map(q -> q.getOrdre() + ":" + q.getEnunciat()).toList();
    }

    @Test
    void afegeix_al_mig_al_principi_i_al_final_renumera_sense_xocar() {
        prepara();

        service.afegeix(exam.getId(), curta("mig", 2), prof);
        assertThat(enunciats()).containsExactly("1:P1", "2:mig", "3:P2", "4:P3");

        exam = examRepository.findById(exam.getId()).orElseThrow();
        reconnecta();
        service.afegeix(exam.getId(), curta("inici", 1), prof);
        assertThat(enunciats()).containsExactly("1:inici", "2:P1", "3:mig", "4:P2", "5:P3");

        exam = examRepository.findById(exam.getId()).orElseThrow();
        reconnecta();
        service.afegeix(exam.getId(), curta("final", null), prof);
        assertThat(enunciats()).containsExactly("1:inici", "2:P1", "3:mig", "4:P2", "5:P3", "6:final");
    }

    @Test
    void mou_i_elimina_deixen_l_ordre_consecutiu() {
        prepara();
        Question p3 = exam.getQuestions().get(2);

        service.mou(exam.getId(), p3.getId(), 1, prof);
        assertThat(enunciats()).containsExactly("1:P3", "2:P1", "3:P2");

        exam = examRepository.findById(exam.getId()).orElseThrow();
        reconnecta();
        service.elimina(exam.getId(), exam.getQuestions().get(1).getId(), prof);   // P1
        assertThat(enunciats()).containsExactly("1:P3", "2:P2");
    }

    @Test
    void elimina_esborra_els_fitxers_de_la_pregunta_a_la_bd() {
        prepara();
        Question p1 = exam.getQuestions().get(0);
        fileRepository.saveAndFlush(QuestionFile.builder().question(p1).filename("img.png")
                .storedPath("/no/existeix/img.png").contentType("image/png").fileSize(10).build());

        service.elimina(exam.getId(), p1.getId(), prof);
        em.flush();

        assertThat(fileRepository.findByQuestionId(p1.getId())).isEmpty();
    }

    /** El servei treballa amb l'entitat que li dóna ExamService: la tornem a configurar després de recarregar-la. */
    private void reconnecta() {
        ExamService examService = mock(ExamService.class);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        service = new EditorPreguntesService(examService, new MarkdownExamParser(), questionRepository,
                fileRepository, sessionRepository);
        ReflectionTestUtils.setField(service, "imatgeMaxBytes", 5L * 1024 * 1024);
    }
}
