package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultes de matrícules i impartiments que alimenten el filtre d'alumnes dels grups. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class MatriculaRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestEntityManager      em;
    @Autowired MatriculaRepository    matriculaRepository;
    @Autowired ImparticioRepository   imparticioRepository;

    Modul asix0483;
    Modul asix0484;
    Modul daw0485;
    User ana;
    User biel;
    User prof;

    @BeforeEach
    void setUp() {
        Departament dep = em.persist(Departament.builder().nom("Informàtica").build());
        Cicle asix = em.persist(Cicle.builder().codi("ASIX").nom("ASIX").departament(dep).build());
        Cicle daw  = em.persist(Cicle.builder().codi("DAW").nom("DAW").departament(dep).build());
        asix0483 = em.persist(Modul.builder().codi("0483").nom("Sistemes").cicle(asix).build());
        asix0484 = em.persist(Modul.builder().codi("0484").nom("Bases de dades").cicle(asix).build());
        daw0485  = em.persist(Modul.builder().codi("0485").nom("Programació").cicle(daw).build());

        ana  = em.persist(User.builder().name("Ana").email("ana@test.cat").role(Role.STUDENT).build());
        biel = em.persist(User.builder().name("Biel").email("biel@test.cat").role(Role.STUDENT).build());
        prof = em.persist(User.builder().name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build());

        em.persist(Matricula.builder().alumne(ana).modul(asix0483).curs("2026-27").build());
        em.persist(Matricula.builder().alumne(ana).modul(asix0484).curs("2026-27").build());
        em.persist(Matricula.builder().alumne(biel).modul(daw0485).curs("2026-27").build());
        em.persist(Matricula.builder().alumne(biel).modul(asix0483).curs("2025-26").build());
        em.persist(Imparticio.builder().professor(prof).modul(asix0483).curs("2026-27").build());
        em.persist(Imparticio.builder().professor(prof).modul(asix0483).curs("2025-26").build());
        em.persist(Imparticio.builder().professor(prof).modul(daw0485).curs("2026-27").build());
        em.flush();
        em.clear();
    }

    @Test
    void findAllAmbDetall_retorna_totes_amb_alumne_i_modul_carregats_i_ordenades() {
        List<Matricula> r = matriculaRepository.findAllAmbDetall();

        assertThat(r).hasSize(4);
        // Curs més recent primer, i dins el curs per codi de mòdul
        assertThat(r).extracting(Matricula::getCurs)
                .containsExactly("2026-27", "2026-27", "2026-27", "2025-26");
        assertThat(r).extracting(m -> m.getModul().getCodi())
                .containsExactly("0483", "0484", "0485", "0483");
        // Alumne i mòdul (el que necessita MatriculaDto) ja estan carregats: no cal sessió oberta
        em.clear();
        assertThat(r.get(0).getAlumne().getName()).isEqualTo("Ana");
        assertThat(r.get(0).getModul().getNom()).isEqualTo("Sistemes");
    }

    @Test
    void findByModulIdsAmbDetall_nomes_retorna_els_moduls_demanats() {
        List<Matricula> r = matriculaRepository.findByModulIdsAmbDetall(List.of(asix0484.getId()));

        assertThat(r).singleElement().satisfies(m -> {
            assertThat(m.getAlumne().getEmail()).isEqualTo("ana@test.cat");
            assertThat(m.getModul().getCodi()).isEqualTo("0484");
        });
    }

    @Test
    void findByModulIdsAmbDetall_amb_diversos_moduls_i_cursos() {
        List<Matricula> r = matriculaRepository.findByModulIdsAmbDetall(
                List.of(asix0483.getId(), daw0485.getId()));

        assertThat(r).hasSize(3);
        assertThat(r).extracting(m -> m.getAlumne().getName()).containsExactlyInAnyOrder("Ana", "Biel", "Biel");
        assertThat(r).noneMatch(m -> m.getModul().getCodi().equals("0484"));
    }

    @Test
    void findModulIdsByProfessorId_retorna_cada_modul_un_cop_encara_que_el_imparteixi_en_diversos_cursos() {
        List<java.util.UUID> ids = imparticioRepository.findModulIdsByProfessorId(prof.getId());

        assertThat(ids).containsExactlyInAnyOrder(asix0483.getId(), daw0485.getId());
    }

    @Test
    void findModulIdsByProfessorId_professor_sense_impartiments_retorna_buit() {
        User altre = em.persist(User.builder().name("Altre").email("altre@test.cat").role(Role.PROFESSOR).build());

        assertThat(imparticioRepository.findModulIdsByProfessorId(altre.getId())).isEmpty();
    }
}
