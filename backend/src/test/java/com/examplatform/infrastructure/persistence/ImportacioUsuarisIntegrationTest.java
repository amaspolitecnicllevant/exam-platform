package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.ConfiguracioService;
import com.examplatform.domain.service.ImportacioUsuarisService;
import com.examplatform.dto.ImportacioDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La importació d'alumnes contra PostgreSQL real: comptes, matrícules i grups. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class ImportacioUsuarisIntegrationTest {

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
    @Autowired MatriculaRepository matriculaRepository;
    @Autowired GrupRepository grupRepository;
    @Autowired ImparticioRepository imparticioRepository;

    @Test
    @SuppressWarnings("deprecation")
    void importa_matricula_i_crea_el_grup_a_la_bd() throws Exception {
        User admin = userRepository.save(User.builder().name("Admin").email("admin@test.cat").role(Role.ADMIN).build());
        Departament d = em.persist(Departament.builder().nom("Informàtica").build());
        Cicle c = em.persist(Cicle.builder().codi("DAW").nom("DAW").departament(d).build());
        Modul m = em.persist(Modul.builder().codi("0483").nom("Sistemes").cicle(c).build());
        User existent = userRepository.save(User.builder().name("Carla").email("carla@test.cat")
                .passwordHash("antiga").role(Role.STUDENT).build());
        em.flush();

        ConfiguracioService config = mock(ConfiguracioService.class);
        ConfiguracioSistema cs = new ConfiguracioSistema();
        cs.setCursActiu("2026-27");
        when(config.get()).thenReturn(cs);
        ImportacioUsuarisService service = new ImportacioUsuarisService(userRepository,
                NoOpPasswordEncoder.getInstance(), modulRepository, matriculaRepository, grupRepository,
                imparticioRepository, config);

        String csv = "nom;email;contrasenya;modul;grup\n"
                + "Anna Puig;Anna@Test.cat;;0483;1r DAW\n"
                + "Biel Mas;biel@test.cat;contrasenya1;0483;1r DAW\n"
                + "Carla;CARLA@test.cat;;0483;1r DAW\n";
        ImportacioDto r = service.importa(csv.getBytes(StandardCharsets.UTF_8), null, admin);
        em.flush();
        em.clear();

        assertThat(r.errors()).isEmpty();
        assertThat(r.created()).isEqualTo(2);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.matriculats()).isEqualTo(3);
        assertThat(r.grupsCreats()).containsExactly("1r DAW");
        assertThat(r.contrasenyes()).extracting(ImportacioDto.Credencial::email).containsExactly("anna@test.cat");

        assertThat(userRepository.findByEmail("anna@test.cat")).isPresent();
        assertThat(userRepository.findById(existent.getId()).orElseThrow().getPasswordHash()).isEqualTo("antiga");
        assertThat(matriculaRepository.findByModulIdAndCurs(m.getId(), "2026-27")).hasSize(3);
        Grup grup = grupRepository.findByCreatedByIdWithStudents(admin.getId()).get(0);
        assertThat(grup.getModul().getId()).isEqualTo(m.getId());
        assertThat(grup.getStudents()).extracting(User::getEmail)
                .containsExactlyInAnyOrder("anna@test.cat", "biel@test.cat", "carla@test.cat");
    }
}
