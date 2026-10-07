package com.examplatform.domain.service.emmagatzematge;

import com.examplatform.dto.EmmagatzematgeDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgregadorEspaiTest {

    static final UUID ANNA = UUID.randomUUID(), BERTA = UUID.randomUUID();
    static final UUID INF = UUID.randomUUID(), SAN = UUID.randomUUID();
    static final UUID ASIX = UUID.randomUUID(), DAW = UUID.randomUUID(), CURES = UUID.randomUUID();
    static final UUID M1 = UUID.randomUUID(), M2 = UUID.randomUUID(), M3 = UUID.randomUUID(), M4 = UUID.randomUUID();

    private static UsExamen examen(String titol, UUID prof, String nomProf, UUID modul, String nomModul, UUID cicle, String nomCicle,
                                   UUID dep, String nomDep, int fp, long mp, int l, long ml) {
        return new UsExamen(UUID.randomUUID(), titol, prof, nomProf, modul, nomModul, cicle, nomCicle, dep, nomDep, fp, mp, l, ml);
    }

    /** Anna: 2 exàmens d'ASIX (M1: 100 B de preguntes + 900 B de lliuraments; M2: 0 + 300). Berta: 1 de DAW (M3) i 1 sense mòdul. */
    private static List<UsExamen> dades() {
        return List.of(
                examen("Parcial 1", ANNA, "Anna", M1, "0483 — Sistemes", ASIX, "ASIX", INF, "Informàtica", 2, 100, 30, 900),
                examen("Parcial 2", ANNA, "Anna", M2, "0484 — Bases de dades", ASIX, "ASIX", INF, "Informàtica", 0, 0, 10, 300),
                examen("Pràctica", BERTA, "Berta", M3, "0485 — Programació", DAW, "DAW", INF, "Informàtica", 1, 50, 5, 700),
                examen("Esborrany", BERTA, "Berta", null, null, null, null, null, null, 0, 0, 0, 0),
                examen("Infermeria", ANNA, "Anna", M4, "0600 — Cures", CURES, "Cures", SAN, "Sanitat", 0, 0, 1, 20));
    }

    private static EmmagatzematgeDto.Fila fila(EmmagatzematgeDto.Taula t, String nom) {
        return t.files().stream().filter(f -> f.nom().equals(nom)).findFirst().orElseThrow();
    }

    @Test
    void per_professor_suma_preguntes_i_lliuraments_i_ordena_de_mes_a_menys() {
        var t = AgregadorEspai.agrupa(dades(), Agrupacio.PROFESSOR);

        assertThat(t.agrupa()).isEqualTo("professor");
        assertThat(t.files()).extracting(EmmagatzematgeDto.Fila::nom).containsExactly("Anna", "Berta");
        var anna = fila(t, "Anna");
        assertThat(anna.examens()).isEqualTo(3);
        assertThat(anna.fitxersPregunta()).isEqualTo(2);
        assertThat(anna.midaPregunta()).isEqualTo(100);
        assertThat(anna.lliuraments()).isEqualTo(41);
        assertThat(anna.midaLliuraments()).isEqualTo(1220);
        assertThat(anna.total()).isEqualTo(1320);
        assertThat(fila(t, "Berta").total()).isEqualTo(750);
    }

    @Test
    void per_departament_agrupa_i_deixa_els_examens_sense_modul_a_part() {
        var t = AgregadorEspai.agrupa(dades(), Agrupacio.DEPARTAMENT);

        assertThat(fila(t, "Informàtica").total()).isEqualTo(100 + 900 + 300 + 50 + 700);
        assertThat(fila(t, "Informàtica").examens()).isEqualTo(3);
        assertThat(fila(t, "Sanitat").total()).isEqualTo(20);
        assertThat(fila(t, AgregadorEspai.SENSE_MODUL).examens()).isEqualTo(1);
        assertThat(fila(t, AgregadorEspai.SENSE_MODUL).total()).isZero();
    }

    @Test
    void per_cicle_i_per_modul() {
        var cicles = AgregadorEspai.agrupa(dades(), Agrupacio.CICLE);
        assertThat(fila(cicles, "ASIX").total()).isEqualTo(1300);
        assertThat(fila(cicles, "ASIX").examens()).isEqualTo(2);
        assertThat(fila(cicles, "ASIX").detall()).isEqualTo("Informàtica");
        assertThat(fila(cicles, "DAW").total()).isEqualTo(750);

        var moduls = AgregadorEspai.agrupa(dades(), Agrupacio.MODUL);
        assertThat(moduls.files()).extracting(EmmagatzematgeDto.Fila::nom)
                .containsExactly("0483 — Sistemes", "0485 — Programació", "0484 — Bases de dades", "0600 — Cures", AgregadorEspai.SENSE_MODUL);
        assertThat(fila(moduls, "0483 — Sistemes").detall()).isEqualTo("ASIX");
    }

    @Test
    void per_examen_hi_ha_una_fila_per_examen_amb_el_professor_al_detall() {
        var t = AgregadorEspai.agrupa(dades(), Agrupacio.EXAMEN);

        assertThat(t.files()).hasSize(5);
        assertThat(t.files().get(0).nom()).isEqualTo("Parcial 1");
        assertThat(t.files().get(0).detall()).isEqualTo("Anna");
        assertThat(t.files().get(0).examens()).isEqualTo(1);
    }

    @Test
    void el_total_es_el_mateix_amb_qualsevol_agrupacio() {
        for (Agrupacio a : Agrupacio.values()) {
            var t = AgregadorEspai.agrupa(dades(), a);
            assertThat(t.total().total()).as(a.name()).isEqualTo(2070);
            assertThat(t.total().examens()).as(a.name()).isEqualTo(5);
            assertThat(t.files().stream().mapToLong(EmmagatzematgeDto.Fila::total).sum()).as(a.name()).isEqualTo(2070);
            assertThat(t.files().stream().mapToInt(EmmagatzematgeDto.Fila::examens).sum()).as(a.name()).isEqualTo(5);
        }
    }

    @Test
    void sense_examens_la_taula_es_buida_amb_total_zero() {
        var t = AgregadorEspai.agrupa(List.of(), Agrupacio.PROFESSOR);

        assertThat(t.files()).isEmpty();
        assertThat(t.total().total()).isZero();
        assertThat(t.total().examens()).isZero();
    }

    @Test
    void a_igualtat_d_espai_s_ordena_pel_nom_sense_distingir_majuscules() {
        var t = AgregadorEspai.agrupa(List.of(
                examen("a", UUID.randomUUID(), "berta", null, null, null, null, null, null, 0, 0, 0, 0),
                examen("b", UUID.randomUUID(), "Àlex", null, null, null, null, null, null, 0, 0, 0, 0),
                examen("c", UUID.randomUUID(), "Carla", null, null, null, null, null, null, 0, 0, 0, 0)), Agrupacio.PROFESSOR);

        assertThat(t.files()).extracting(EmmagatzematgeDto.Fila::nom).containsExactly("berta", "Carla", "Àlex");
    }

    @Test
    void dos_professors_amb_el_mateix_nom_no_es_barregen() {
        var t = AgregadorEspai.agrupa(List.of(
                examen("a", UUID.randomUUID(), "Joan Soler", null, null, null, null, null, null, 0, 10, 0, 0),
                examen("b", UUID.randomUUID(), "Joan Soler", null, null, null, null, null, null, 0, 20, 0, 0)), Agrupacio.PROFESSOR);

        assertThat(t.files()).hasSize(2);
    }

    @Test
    void un_professor_sense_nom_surt_com_a_sense_nom() {
        var t = AgregadorEspai.agrupa(List.of(
                examen("a", UUID.randomUUID(), null, null, null, null, null, null, null, 0, 5, 0, 0)), Agrupacio.PROFESSOR);

        assertThat(t.files().get(0).nom()).isEqualTo("(sense nom)");
    }

    @Test
    void agrupacio_accepta_variants_i_rebutja_el_que_no_coneix() {
        assertThat(Agrupacio.de(null)).isEqualTo(Agrupacio.PROFESSOR);
        assertThat(Agrupacio.de("")).isEqualTo(Agrupacio.PROFESSOR);
        assertThat(Agrupacio.de("Modul")).isEqualTo(Agrupacio.MODUL);
        assertThat(Agrupacio.de("mòdul")).isEqualTo(Agrupacio.MODUL);
        assertThat(Agrupacio.de(" DEPARTAMENT ")).isEqualTo(Agrupacio.DEPARTAMENT);
        assertThatThrownBy(() -> Agrupacio.de("alumne")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alumne").hasMessageContaining("professor, departament, cicle, modul, examen");
    }
}
