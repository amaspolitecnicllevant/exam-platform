package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.ExamService;
import com.examplatform.domain.service.ProgramacioService;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ProgramacioDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Competències i unitats de treball contra PostgreSQL real: consultes JPQL, claus foranes i conversió de les dades antigues. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class ProgramacioIntegrationTest {

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
    @Autowired ModulRepository modulRepository;
    @Autowired CicleRepository cicleRepository;
    @Autowired DepartamentRepository departamentRepository;
    @Autowired CompetenciaRepository competenciaRepository;
    @Autowired UnitatTreballRepository unitatRepository;
    @Autowired QuestionRepository questionRepository;
    @Autowired ExamRepository examRepository;
    @Autowired ImparticioRepository imparticioRepository;
    @Autowired JdbcTemplate jdbc;

    User admin;
    Modul modul;
    Exam exam;
    ExamService examService;
    ProgramacioService service;

    private void prepara() {
        admin = userRepository.save(User.builder().name("Admin").email("admin@test.cat").role(Role.ADMIN).build());
        Departament dep = departamentRepository.save(Departament.builder().nom("Informàtica").build());
        Cicle cicle = cicleRepository.save(Cicle.builder().codi("ASIX").nom("ASIX").departament(dep).build());
        modul = modulRepository.save(Modul.builder().codi("0485").nom("Mòdul").cicle(cicle).build());
        exam = Exam.builder().title("Examen").durada(60).createdBy(admin).modul(modul).rawMd("x").questions(new ArrayList<>()).build();
        exam.getQuestions().add(Question.builder().exam(exam).ordre(1).tipus(QuestionType.SHORT)
                .enunciat("P1").punts(new BigDecimal("10")).competencia("CO1").build());
        exam = examRepository.saveAndFlush(exam);
        examService = mock(ExamService.class);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        service = new ProgramacioService(modulRepository, competenciaRepository, unitatRepository,
                questionRepository, examRepository, imparticioRepository, examService);
    }

    private static ProgramacioDto.Peticio peticio(List<String> cos, List<ProgramacioDto.UnitatPeticio> uts) {
        return new ProgramacioDto.Peticio(
                cos.stream().map(c -> new ProgramacioDto.CompetenciaPeticio(c, "Descripció " + c)).toList(), uts);
    }

    private static ProgramacioDto.UnitatPeticio ut(String codi, String... cos) {
        return new ProgramacioDto.UnitatPeticio(codi, "Nom " + codi, List.of(cos));
    }

    @Test
    void desa_i_llegeix_la_programacio_amb_les_relacions_unitat_competencia() {
        prepara();

        ProgramacioDto res = service.substitueix(modul.getId(),
                peticio(List.of("CO1", "CO2", "CO3"), List.of(ut("UT01", "CO1", "CO2"), ut("UT02", "CO3"))), admin);
        em.flush();
        em.clear();

        assertThat(res.competencies()).extracting(ProgramacioDto.Competencia::codi).containsExactly("CO1", "CO2", "CO3");
        ProgramacioDto llegida = service.obte(modul.getId(), admin);
        assertThat(llegida.unitats()).extracting(ProgramacioDto.Unitat::codi).containsExactly("UT01", "UT02");
        assertThat(llegida.unitats().get(0).competencies()).containsExactly("CO1", "CO2");
        assertThat(llegida.unitats().get(1).competencies()).containsExactly("CO3");
        assertThat(modulRepository.findById(modul.getId()).orElseThrow().getCompetencies()).hasSize(3);
    }

    @Test
    void tornar_a_desar_conserva_els_ids_i_pot_canviar_les_relacions() {
        prepara();
        service.substitueix(modul.getId(), peticio(List.of("CO1", "CO2"), List.of(ut("UT01", "CO1"))), admin);
        em.flush();
        em.clear();
        UnitatTreball abans = unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId()).get(0);

        service.substitueix(modul.getId(), peticio(List.of("CO1", "CO2"), List.of(ut("UT01", "CO1", "CO2"))), admin);
        em.flush();
        em.clear();

        UnitatTreball despres = unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId()).get(0);
        assertThat(despres.getId()).isEqualTo(abans.getId());
        assertThat(despres.getCompetencies()).hasSize(2);
    }

    @Test
    void la_consulta_de_preguntes_per_competencia_funciona() {
        prepara();

        assertThat(questionRepository.comptaCompetencia(modul.getId(), "CO1")).isEqualTo(1);
        assertThat(questionRepository.comptaCompetencia(modul.getId(), "CO2")).isZero();
    }

    @Test
    void no_es_pot_treure_una_competencia_amb_preguntes_ni_una_unitat_avaluada_per_un_examen() {
        prepara();
        service.substitueix(modul.getId(), peticio(List.of("CO1", "CO2"), List.of(ut("UT01", "CO1"), ut("UT02", "CO2"))), admin);
        em.flush();
        em.clear();
        UnitatTreball ut01 = unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId()).get(0);
        exam = examRepository.findById(exam.getId()).orElseThrow();
        when(examService.getEntity(exam.getId())).thenReturn(exam);

        ExamDto dto = service.assignaUnitats(exam.getId(), List.of(ut01.getId()), admin);
        em.flush();
        em.clear();

        assertThat(dto.unitats()).extracting(ProgramacioDto.Unitat::codi).containsExactly("UT01");
        assertThat(examRepository.comptaPerUnitat(ut01.getId())).isEqualTo(1);
        // CO1 té una pregunta
        assertThatThrownBy(() -> service.substitueix(modul.getId(),
                peticio(List.of("CO2"), List.of(ut("UT01", "CO2"), ut("UT02", "CO2"))), admin))
                .hasMessageContaining("CO1");
        // UT01 la avalua un examen
        assertThatThrownBy(() -> service.substitueix(modul.getId(),
                peticio(List.of("CO1", "CO2"), List.of(ut("UT02", "CO2"))), admin))
                .hasMessageContaining("UT01");
    }

    @Test
    void esborrar_l_examen_o_el_modul_no_deixa_files_orfes() {
        prepara();
        service.substitueix(modul.getId(), peticio(List.of("CO1"), List.of(ut("UT01", "CO1"))), admin);
        em.flush();
        em.clear();
        UnitatTreball ut01 = unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId()).get(0);
        exam = examRepository.findById(exam.getId()).orElseThrow();
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        service.assignaUnitats(exam.getId(), List.of(ut01.getId()), admin);
        em.flush();
        em.clear();

        jdbc.update("DELETE FROM exams WHERE id = ?", exam.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_unitats", Integer.class)).isZero();

        jdbc.update("DELETE FROM moduls WHERE id = ?", modul.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencies", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unitats_treball", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unitat_competencies", Integer.class)).isZero();
    }

    /** La conversió RA→CO de la migració V41, aplicada a dades que imiten les existents. */
    @Test
    void la_migracio_converteix_els_ra_en_co_i_deixa_la_resta_tal_com_esta() throws Exception {
        prepara();
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V41__competencies_i_unitats.sql"));
        String update = sql.substring(sql.indexOf("UPDATE questions"));
        String[] originals = {"RA1", "ra2", " RA3 ", "RA 4", "RA10", "RA1.2", "Tema 5", "CO7", "RAx"};
        String[] esperats  = {"CO1", "CO2", "CO3", "CO4", "CO10", "RA1.2", "Tema 5", "CO7", "RAx"};
        for (int i = 0; i < originals.length; i++) {
            Question q = Question.builder().exam(exam).ordre(10 + i).tipus(QuestionType.SHORT)
                    .enunciat("x").punts(BigDecimal.ONE).competencia(originals[i]).build();
            exam.getQuestions().add(q);
        }
        examRepository.saveAndFlush(exam);

        jdbc.update(update);
        em.clear();

        for (int i = 0; i < originals.length; i++) {
            String valor = jdbc.queryForObject("SELECT competencia FROM questions WHERE exam_id = ? AND ordre = ?", String.class,
                    exam.getId(), 10 + i);
            assertThat(valor).as("«" + originals[i] + "»").isEqualTo(esperats[i]);
        }
    }
}
