package com.examplatform.domain.service.emmagatzematge;

import com.examplatform.domain.model.*;
import com.examplatform.dto.EmmagatzematgeDto;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultes de l'auditoria d'espai contra un PostgreSQL real. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class EmmagatzematgeServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestEntityManager em;
    @TempDir Path tmp;

    EmmagatzematgeService service;
    Exam examAnna, examBerta, examBuit;

    @BeforeEach
    void setUp() {
        service = new EmmagatzematgeService(em.getEntityManager(), tmp.toString());
        Departament inf = em.persist(Departament.builder().nom("Informàtica").build());
        Cicle asix = em.persist(Cicle.builder().codi("ASIX").nom("ASIX").departament(inf).build());
        Modul sistemes = em.persist(Modul.builder().codi("0483").nom("Sistemes").cicle(asix).build());
        User anna = em.persist(User.builder().name("Anna").email("anna@x.cat").role(Role.PROFESSOR).build());
        User berta = em.persist(User.builder().name("Berta").email("berta@x.cat").role(Role.PROFESSOR).build());

        examAnna = exam("Parcial Anna", anna, sistemes);
        examBerta = exam("Esborrany Berta", berta, null);
        examBuit = exam("Buit Anna", anna, sistemes);

        Question q = em.persist(Question.builder().exam(examAnna).ordre(1).tipus(QuestionType.FILE_UPLOAD)
                .enunciat("x").punts(BigDecimal.TEN).build());
        Question qBerta = em.persist(Question.builder().exam(examBerta).ordre(1).tipus(QuestionType.SHORT)
                .enunciat("x").punts(BigDecimal.TEN).build());

        em.persist(QuestionFile.builder().question(q).filename("a.txt").storedPath("/p/a").fileSize(100).build());
        em.persist(QuestionFile.builder().question(q).filename("b.txt").storedPath("/p/b").fileSize(50).build());
        em.persist(QuestionFile.builder().question(qBerta).filename("c.txt").storedPath("/p/c").fileSize(7).build());

        lliura(examAnna, q, "alu1@x.cat", 1000L);
        lliura(examAnna, q, "alu2@x.cat", 500L);
        // Resposta sense fitxer: no compta
        ExamSession s3 = sessio(examAnna, "alu3@x.cat");
        em.persist(Answer.builder().session(s3).question(q).contingut("text").build());
        em.flush();
        em.clear();
    }

    private Exam exam(String titol, User autor, Modul modul) {
        return em.persist(Exam.builder().rawMd("# x").title(titol).durada(60).status(ExamStatus.PUBLISHED)
                .createdBy(autor).modul(modul).penalitzacioChoice(BigDecimal.ZERO).questions(new ArrayList<>()).build());
    }

    private ExamSession sessio(Exam exam, String email) {
        User alumne = em.persist(User.builder().name(email).email(email).role(Role.STUDENT).build());
        return em.persist(ExamSession.builder().exam(exam).student(alumne).status(SessionStatus.SUBMITTED).build());
    }

    private void lliura(Exam exam, Question q, String email, long mida) {
        ExamSession s = sessio(exam, email);
        em.persist(Answer.builder().session(s).question(q).contingut("f.docx").fitxerNom("f.docx")
                .fitxerRuta("/opt/exam-files/answers/" + email).fitxerMida(mida).fitxerPujatEl(LocalDateTime.now()).build());
    }

    private Map<String, UsExamen> perTitol() {
        return service.usPerExamen().stream().collect(Collectors.toMap(UsExamen::examTitol, u -> u));
    }

    @Test
    void suma_els_fitxers_de_preguntes_i_els_lliuraments_de_cada_examen() {
        UsExamen u = perTitol().get("Parcial Anna");

        assertThat(u.fitxersPregunta()).isEqualTo(2);
        assertThat(u.midaPregunta()).isEqualTo(150);
        assertThat(u.lliuraments()).isEqualTo(2);              // el de text no compta
        assertThat(u.midaLliuraments()).isEqualTo(1500);
        assertThat(u.total()).isEqualTo(1650);
    }

    @Test
    void porta_professor_mod_cicle_i_departament_de_cada_examen() {
        UsExamen u = perTitol().get("Parcial Anna");

        assertThat(u.professor()).isEqualTo("Anna");
        assertThat(u.modul()).isEqualTo("0483 — Sistemes");
        assertThat(u.cicle()).isEqualTo("ASIX");
        assertThat(u.departament()).isEqualTo("Informàtica");
    }

    @Test
    void un_examen_sense_modul_hi_es_amb_els_camps_buits_i_el_seu_propi_espai() {
        UsExamen u = perTitol().get("Esborrany Berta");

        assertThat(u.modul()).isNull();
        assertThat(u.cicle()).isNull();
        assertThat(u.departament()).isNull();
        assertThat(u.fitxersPregunta()).isEqualTo(1);
        assertThat(u.midaPregunta()).isEqualTo(7);
        assertThat(u.lliuraments()).isZero();
    }

    @Test
    void un_examen_sense_cap_fitxer_surt_igualment_amb_zeros() {
        UsExamen u = perTitol().get("Buit Anna");

        assertThat(u.total()).isZero();
        assertThat(u.fitxersPregunta()).isZero();
        assertThat(u.lliuraments()).isZero();
    }

    @Test
    void l_espai_d_un_examen_no_es_barreja_amb_el_d_un_altre() {
        var ex = perTitol();

        assertThat(ex).hasSize(3);
        assertThat(ex.values().stream().mapToLong(UsExamen::total).sum()).isEqualTo(150 + 1500 + 7);
    }

    @Test
    void l_auditoria_per_professor_i_per_modul_quadra_amb_el_total() {
        EmmagatzematgeDto per_prof = service.auditoria(Agrupacio.PROFESSOR);
        EmmagatzematgeDto per_modul = service.auditoria(Agrupacio.MODUL);

        assertThat(per_prof.taula().files()).extracting(EmmagatzematgeDto.Fila::nom).containsExactly("Anna", "Berta");
        assertThat(per_prof.taula().files().get(0).total()).isEqualTo(1650);
        assertThat(per_prof.taula().files().get(0).examens()).isEqualTo(2);
        assertThat(per_prof.taula().total().total()).isEqualTo(1657);
        assertThat(per_modul.taula().files()).extracting(EmmagatzematgeDto.Fila::nom)
                .containsExactly("0483 — Sistemes", AgregadorEspai.SENSE_MODUL);
        assertThat(per_modul.taula().total().total()).isEqualTo(1657);
    }
}
