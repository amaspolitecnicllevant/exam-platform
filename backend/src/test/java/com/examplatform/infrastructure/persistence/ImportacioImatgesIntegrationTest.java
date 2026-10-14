package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.ImportadorImatgesService;
import com.examplatform.infrastructure.parser.MarkdownExamParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La importació d'imatges contra PostgreSQL real: els fitxers queden lligats a la pregunta i l'enunciat els referencia. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class ImportacioImatgesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};

    @Autowired TestEntityManager em;
    @Autowired UserRepository userRepository;
    @Autowired ExamRepository examRepository;
    @Autowired QuestionFileRepository fileRepository;
    @TempDir Path disc;

    @Test
    void importa_un_examen_amb_imatges_externes_i_les_lliga_a_les_preguntes() throws Exception {
        User prof = userRepository.save(User.builder().name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());
        String md = """
                # Examen amb imatges
                durada: 60
                ---

                ## 1. [short] [pts:5]
                Observa la xarxa ![Topologia](https://exemple.cat/xarxa.png) i explica-la.

                ## 2. [short] [pts:5]
                Altra vegada ![Topologia](https://exemple.cat/xarxa.png)
                """;
        Exam exam = new MarkdownExamParser().parse(md, prof);
        exam.getQuestions().forEach(q -> q.setExam(exam));
        Exam desat = examRepository.save(exam);

        ImportadorImatgesService importador = new ImportadorImatgesService((url, max) -> PNG, fileRepository);
        ReflectionTestUtils.setField(importador, "filesHostPath", disc.toString());
        ReflectionTestUtils.setField(importador, "imatgeMaxBytes", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(importador, "maximImatges", 60);
        importador.incorpora(desat);
        em.flush();
        em.clear();

        Exam llegit = examRepository.findById(desat.getId()).orElseThrow();
        for (Question q : llegit.getQuestions()) {
            List<QuestionFile> fitxers = fileRepository.findByQuestionId(q.getId());
            assertThat(fitxers).hasSize(1);
            QuestionFile f = fitxers.get(0);
            assertThat(q.getEnunciat()).contains("![Topologia](fitxer:" + f.getId() + ")").doesNotContain("https://");
            assertThat(Files.readAllBytes(Path.of(f.getStoredPath()))).isEqualTo(PNG);
            assertThat(f.getContentType()).isEqualTo("image/png");
        }
    }
}
