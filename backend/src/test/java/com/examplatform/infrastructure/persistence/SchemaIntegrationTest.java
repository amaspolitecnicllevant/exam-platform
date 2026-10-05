package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
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

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class SchemaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DepartamentRepository departamentRepository;
    @Autowired CicleRepository       cicleRepository;
    @Autowired ModulRepository       modulRepository;
    @Autowired ImparticioRepository  imparticioRepository;
    @Autowired MatriculaRepository   matriculaRepository;
    @Autowired UserRepository        userRepository;
    @Autowired GrupRepository        grupRepository;

    // ── Flyway + entitats noves ───────────────────────────────────────────────

    @Test
    void totes_les_taules_noves_existeixen_i_els_repositoris_funcionen() {
        assertThat(departamentRepository.count()).isZero();
        assertThat(cicleRepository.count()).isZero();
        assertThat(modulRepository.count()).isZero();
        assertThat(imparticioRepository.count()).isZero();
        assertThat(matriculaRepository.count()).isZero();
    }

    // ── Relació Departament → Cicle → Modul ──────────────────────────────────

    @Test
    void pot_persistir_jerarquia_departament_cicle_modul() {
        Departament dep = departamentRepository.save(
                Departament.builder().nom("Informàtica").build());

        Cicle cicle = cicleRepository.save(
                Cicle.builder().codi("ASIX").nom("Administració de Sistemes").departament(dep).build());

        Modul modul = modulRepository.save(
                Modul.builder().codi("0483").nom("Sistemes Informàtics").cicle(cicle).build());

        assertThat(modulRepository.findByCodi("0483")).isPresent()
                .get().extracting(m -> m.getCicle().getDepartament().getNom())
                .isEqualTo("Informàtica");

        assertThat(modulRepository.findByDepartamentId(dep.getId()))
                .hasSize(1)
                .first().extracting(Modul::getCodi).isEqualTo("0483");
    }

    // ── Imparticio ────────────────────────────────────────────────────────────

    @Test
    void professorImparteixModul_retorna_true_si_existeix_imparticio() {
        Departament dep  = departamentRepository.save(Departament.builder().nom("Dep A").build());
        Cicle cicle      = cicleRepository.save(Cicle.builder().codi("DAW").nom("DAW").departament(dep).build());
        Modul modul      = modulRepository.save(Modul.builder().codi("0615").nom("Accés a Dades").cicle(cicle).build());

        User professor = userRepository.save(User.builder()
                .name("Prof Test").email("prof@test.cat")
                .role(Role.PROFESSOR).build());

        imparticioRepository.save(Imparticio.builder()
                .professor(professor).modul(modul).curs("2026-27").build());

        assertThat(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).isTrue();
        assertThat(imparticioRepository.professorImparteixModul(professor.getId(), dep.getId())).isFalse();
    }

    @Test
    void existsByProfessorIdAndModulIdAndCurs_detecta_duplicats() {
        Departament dep = departamentRepository.save(Departament.builder().nom("Dep B").build());
        Cicle cicle     = cicleRepository.save(Cicle.builder().codi("SMX").nom("SMX").departament(dep).build());
        Modul modul     = modulRepository.save(Modul.builder().codi("0224").nom("SO").cicle(cicle).build());

        User professor = userRepository.save(User.builder()
                .name("Prof2").email("prof2@test.cat")
                .role(Role.PROFESSOR).build());

        imparticioRepository.save(Imparticio.builder()
                .professor(professor).modul(modul).curs("2026-27").build());

        assertThat(imparticioRepository.existsByProfessorIdAndModulIdAndCurs(
                professor.getId(), modul.getId(), "2026-27")).isTrue();
        assertThat(imparticioRepository.existsByProfessorIdAndModulIdAndCurs(
                professor.getId(), modul.getId(), "2025-26")).isFalse();
    }

    // ── Matricula per mòdul ───────────────────────────────────────────────────

    @Autowired ProfessorDepartamentRepository profDepRepository;

    @Test
    void alumneMatriculatAModul_retorna_correctament() {
        Departament dep = departamentRepository.save(Departament.builder().nom("Dep C").build());
        Cicle cicle     = cicleRepository.save(Cicle.builder().codi("FPBA").nom("FP Bàsica").departament(dep).build());
        Modul modul     = modulRepository.save(Modul.builder().codi("0001").nom("Mòdul Test").cicle(cicle).build());

        User alumne = userRepository.save(User.builder()
                .name("Alumne Test").email("alumne@test.cat")
                .role(Role.STUDENT).build());

        matriculaRepository.save(Matricula.builder()
                .alumne(alumne).modul(modul).curs("2026-27").build());

        assertThat(matriculaRepository.alumneMatriculatAModul(alumne.getId(), modul.getId(), "2026-27")).isTrue();
        assertThat(matriculaRepository.alumneMatriculatAModul(alumne.getId(), modul.getId(), "2025-26")).isFalse();
    }

    // ── ProfessorDepartament N:M ──────────────────────────────────────────────

    @Test
    void professor_pot_pertanyer_a_dos_departaments() {
        Departament d1 = departamentRepository.save(Departament.builder().nom("Dep D1").build());
        Departament d2 = departamentRepository.save(Departament.builder().nom("Dep D2").build());

        User prof = userRepository.save(User.builder()
                .name("Prof Dos Depts").email("prof2dep@test.cat")
                .role(Role.PROFESSOR).build());

        profDepRepository.save(ProfessorDepartament.builder().professor(prof).departament(d1).esCap(true).build());
        profDepRepository.save(ProfessorDepartament.builder().professor(prof).departament(d2).esCap(false).build());

        assertThat(profDepRepository.findByProfessorId(prof.getId())).hasSize(2);
        assertThat(profDepRepository.findByDepartamentId(d1.getId())).hasSize(1)
                .first().extracting(ProfessorDepartament::isEsCap).isEqualTo(true);
    }

    // ── Grup i Exam amb modul nullable ────────────────────────────────────────

    @Test
    void grup_sense_modul_persisteix_correctament() {
        User prof = userRepository.save(User.builder()
                .name("Prof3").email("prof3@test.cat")
                .role(Role.PROFESSOR).build());

        Grup grup = grupRepository.save(Grup.builder()
                .name("Grup Orfe").createdBy(prof).build());

        assertThat(grupRepository.findById(grup.getId()))
                .isPresent().get()
                .extracting(Grup::getModul).isNull();
    }
}
