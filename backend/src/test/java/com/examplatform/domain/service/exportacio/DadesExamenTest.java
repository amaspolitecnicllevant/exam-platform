package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DadesExamenTest {

    @Test
    void les_preguntes_van_per_ordre_i_sense_seccions() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "5", null);
        f.pregunta(QuestionType.SECTION, "0", null);
        f.pregunta(QuestionType.LONG, "5", null);

        assertThat(f.dades().preguntes()).extracting(q -> q.getTipus())
                .containsExactly(QuestionType.SHORT, QuestionType.LONG);
    }

    @Test
    void els_alumnes_van_per_nom_sense_distingir_majuscules() {
        FixtureExamen f = new FixtureExamen();
        f.entregat("berta", "b@x.cat");
        f.entregat("Àlex", "a@x.cat");
        f.entregat("Carles", "c@x.cat");

        assertThat(f.dades().alumnes()).extracting(a -> a.nom()).containsExactly("Àlex", "berta", "Carles");
    }

    @Test
    void entregats_nomes_te_els_que_han_entregat() {
        FixtureExamen f = new FixtureExamen();
        f.entregat("A", "a@x.cat");
        f.alumne("B", "b@x.cat", com.examplatform.domain.model.SessionStatus.IN_PROGRESS, true);

        assertThat(f.dades().entregats()).hasSize(1);
    }

    // ── codis anònims ─────────────────────────────────────────────────────────

    @Test
    void els_codis_anonims_son_unics_estables_i_no_revelen_el_nom() {
        FixtureExamen f = new FixtureExamen();
        for (int i = 0; i < 120; i++) f.entregat("Alumne Real " + i, "real" + i + "@x.cat");

        Map<UUID, String> a = f.dades().codisAnonims();
        Map<UUID, String> b = f.dades().codisAnonims();

        assertThat(a).hasSize(120).isEqualTo(b);
        assertThat(a.values()).doesNotHaveDuplicates().allMatch(c -> c.matches("Alumne [0-9A-F]{6,}"));
        assertThat(String.join(" ", a.values())).doesNotContain("Real").doesNotContain("real");
    }

    @Test
    void el_codi_d_un_alumne_no_canvia_si_n_entren_mes() {
        FixtureExamen f = new FixtureExamen();
        var s1 = f.entregat("Anna", "a@x.cat");
        f.entregat("Berta", "b@x.cat");
        String abans = f.dades().codisAnonims().get(s1.getId());

        f.entregat("Carla", "c@x.cat");
        f.entregat("Dani", "d@x.cat");

        assertThat(f.dades().codisAnonims().get(s1.getId())).isEqualTo(abans);
    }

    @Test
    void si_dos_codis_coincideixen_s_allarguen_fins_a_ser_unics() {
        FixtureExamen f = new FixtureExamen();
        var s1 = f.entregat("A", "a@x.cat");
        var s2 = f.entregat("B", "b@x.cat");
        s1.setId(UUID.fromString("abcdef12-0000-4000-8000-000000000001"));
        s2.setId(UUID.fromString("abcdef12-0000-4000-8000-000000000002"));

        Map<UUID, String> codis = f.dades().codisAnonims();

        assertThat(codis.values()).doesNotHaveDuplicates();
        assertThat(codis.get(s1.getId())).startsWith("Alumne ABCDEF12");
    }

    // ── noms de fitxer ────────────────────────────────────────────────────────

    @Test
    void nomNet_treu_barres_punts_dobles_i_caracters_estranys_pero_conserva_accents() {
        assertThat(DadesExamen.nomNet("../../etc/passwd", 60)).doesNotContain("/").doesNotContain("..");
        assertThat(DadesExamen.nomNet("C:\\Users\\Pere", 60)).doesNotContain("\\").doesNotContain(":");
        assertThat(DadesExamen.nomNet("Núria Ç. (nuria)", 60)).isEqualTo("Núria Ç. (nuria)");
        assertThat(DadesExamen.nomNet("a\u0000b\nc", 60)).doesNotContain("\u0000").doesNotContain("\n");
    }

    @Test
    void nomNet_retalla_i_no_deixa_mai_un_nom_buit_ni_ocult() {
        assertThat(DadesExamen.nomNet("x".repeat(500), 40)).hasSize(40);
        assertThat(DadesExamen.nomNet("", 40)).isEqualTo("sense-nom");
        assertThat(DadesExamen.nomNet(null, 40)).isEqualTo("sense-nom");
        assertThat(DadesExamen.nomNet("...", 40)).isEqualTo("sense-nom");
        assertThat(DadesExamen.nomNet(".htaccess", 40)).doesNotStartWith(".");
    }

    @Test
    void titolPerFitxer_substitueix_espais_i_es_segur() {
        FixtureExamen f = new FixtureExamen();
        f.exam.setTitle("Parcial UT1: Xarxes / «Part 2»");

        assertThat(f.dades().titolPerFitxer()).doesNotContain(" ").doesNotContain("/").doesNotContain(":");
    }
}
