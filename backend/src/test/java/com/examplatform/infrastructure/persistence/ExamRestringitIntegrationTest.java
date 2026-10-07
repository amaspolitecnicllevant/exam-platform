package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Qui veu un examen publicat sense data: tots els matriculats, o només els assignats si és restringit. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class ExamRestringitIntegrationTest {

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
    @Autowired ExamSessionRepository sessionRepository;

    private User alumne(String email) {
        return userRepository.save(User.builder().name(email).email(email).role(Role.STUDENT).build());
    }

    @Test
    void examen_restringit_nomes_el_veuen_els_assignats_i_el_normal_tots_els_matriculats() {
        User prof = userRepository.save(User.builder().name("Prof").email("p@x.cat").role(Role.PROFESSOR).build());
        Departament d = em.persist(Departament.builder().nom("Informàtica").build());
        Cicle c = em.persist(Cicle.builder().codi("DAW").nom("DAW").departament(d).build());
        Modul m = em.persist(Modul.builder().codi("0483").nom("Sistemes").cicle(c).build());
        User assignat = alumne("assignat@x.cat"), altre = alumne("altre@x.cat"), forassenyat = alumne("fora@x.cat");
        em.persist(Matricula.builder().alumne(assignat).modul(m).curs("2026-27").build());
        em.persist(Matricula.builder().alumne(altre).modul(m).curs("2026-27").build());

        Exam normal = examRepository.save(Exam.builder().title("Normal").durada(60).createdBy(prof).rawMd("x")
                .status(ExamStatus.PUBLISHED).modul(m).questions(new ArrayList<>()).build());
        Exam restringit = examRepository.save(Exam.builder().title("Restringit").durada(60).createdBy(prof).rawMd("x")
                .status(ExamStatus.PUBLISHED).modul(m).restringit(true).questions(new ArrayList<>()).build());
        sessionRepository.save(ExamSession.builder().exam(restringit).student(assignat).build());
        em.flush();
        em.clear();

        assertThat(titols(assignat)).containsExactlyInAnyOrder("Normal", "Restringit");
        assertThat(titols(altre)).containsExactly("Normal");          // matriculat però no assignat
        assertThat(titols(forassenyat)).isEmpty();                    // ni matriculat ni assignat

        // Afegir-lo després (l'alumne que va faltar) l'hi fa aparèixer
        sessionRepository.saveAndFlush(ExamSession.builder().exam(examRepository.findById(restringit.getId()).orElseThrow())
                .student(altre).build());
        assertThat(titols(altre)).containsExactlyInAnyOrder("Normal", "Restringit");
        assertThat(examRepository.findById(restringit.getId()).orElseThrow().isRestringit()).isTrue();
        assertThat(normal.isRestringit()).isFalse();
    }

    private java.util.List<String> titols(User u) {
        return examRepository.findPublishedForStudent(u.getId()).stream().map(Exam::getTitle).toList();
    }
}
