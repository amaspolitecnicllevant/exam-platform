package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Testcontainers
class EquipAulaRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AulaRepository aulaRepository;
    @Autowired EquipAulaRepository equipRepository;
    @Autowired EquipsReferenciaRepository referenciaRepository;
    @Autowired EntityManager em;

    Aula aula;

    @BeforeEach
    void setUp() {
        equipRepository.deleteAll();
        referenciaRepository.deleteAll();
        aulaRepository.deleteAll();
        aula = aulaRepository.save(Aula.builder().nom("A101").xarxaCidr("10.100.94.0/24").build());
    }

    private EquipAula equip(Aula a, String nom) {
        return EquipAula.builder().aula(a).nom(nom).ip("10.100.94.5").darrerInforme(LocalDateTime.of(2026, 10, 9, 12, 0))
                .integritat("a  x\nb  y").integritatResum("r".repeat(64)).arribaPlataforma(true).arribaIsard(false)
                .navegador("Firefox 155").discLliureMb(1234).usuarisDins(0).build();
    }

    @Test
    void es_desa_i_es_llegeix_amb_totes_les_dades_i_s_ordena_per_nom() {
        equipRepository.save(equip(aula, "pc2"));
        equipRepository.save(equip(aula, "pc1"));
        em.flush(); em.clear();

        var equips = equipRepository.findByAulaIdOrderByNomAsc(aula.getId());

        assertThat(equips).extracting(EquipAula::getNom).containsExactly("pc1", "pc2");
        assertThat(equips.get(0).getNavegador()).isEqualTo("Firefox 155");
        assertThat(equips.get(0).getArribaIsard()).isFalse();
        assertThat(equips.get(0).getIntegritat()).isEqualTo("a  x\nb  y");
        assertThat(equipRepository.countByAulaId(aula.getId())).isEqualTo(2);
        assertThat(equipRepository.findByAulaIdAndNom(aula.getId(), "pc1")).isPresent();
    }

    @Test
    void no_hi_pot_haver_dos_ordinadors_amb_el_mateix_nom_a_la_mateixa_aula_pero_si_a_aules_diferents() {
        Aula altra = aulaRepository.save(Aula.builder().nom("A102").xarxaCidr("10.100.95.0/24").build());
        equipRepository.saveAndFlush(equip(aula, "pc1"));
        equipRepository.saveAndFlush(equip(altra, "pc1"));   // altra aula: permès

        assertThatThrownBy(() -> equipRepository.saveAndFlush(equip(aula, "pc1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void esborrar_l_aula_esborra_els_seus_ordinadors() {
        equipRepository.saveAndFlush(equip(aula, "pc1"));
        em.clear();

        em.createNativeQuery("DELETE FROM aules WHERE id = ?1").setParameter(1, aula.getId()).executeUpdate();
        em.clear();

        assertThat(equipRepository.count()).isZero();
    }

    @Test
    void la_restauracio_es_desa_i_es_llegeix() {
        EquipAula e = equip(aula, "pc1");
        e.setRestauracioDemanadaEl(LocalDateTime.of(2026, 10, 9, 13, 0));
        e.setRestauracioResultat("no trobo l'script local");
        e.setRestauracioResultatEl(LocalDateTime.of(2026, 10, 8, 9, 0));
        equipRepository.saveAndFlush(e);
        em.clear();

        EquipAula llegit = equipRepository.findByAulaIdAndNom(aula.getId(), "pc1").orElseThrow();

        assertThat(llegit.getRestauracioDemanadaEl()).isEqualTo(LocalDateTime.of(2026, 10, 9, 13, 0));
        assertThat(llegit.getRestauracioResultat()).isEqualTo("no trobo l'script local");
        assertThat(llegit.getRestauracioResultatEl()).isEqualTo(LocalDateTime.of(2026, 10, 8, 9, 0));
    }

    @Test
    void nomes_pot_haver_hi_una_referencia() {
        referenciaRepository.saveAndFlush(EquipsReferencia.builder().id(EquipsReferencia.ID).integritat("x")
                .integritatResum("r".repeat(64)).origenNom("pc19").fixadaEl(LocalDateTime.now()).build());
        em.clear();

        assertThat(referenciaRepository.findById(EquipsReferencia.ID)).isPresent()
                .get().extracting(EquipsReferencia::getOrigenNom).isEqualTo("pc19");
        assertThatThrownBy(() -> referenciaRepository.saveAndFlush(EquipsReferencia.builder().id((short) 2).integritat("y")
                .integritatResum("s".repeat(64)).fixadaEl(LocalDateTime.now()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
