package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InformeDificultatTest {

    private static final LocalDate DATA = LocalDate.of(2026, 10, 16);

    private static String informe(FixtureExamen f) {
        return InformeDificultat.informe(f.dades(), DATA);
    }

    @Test
    void sense_entregats_nomes_diu_que_no_hi_ha_resultats() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "10", null);
        f.alumne("Anna", "anna@x.cat", SessionStatus.IN_PROGRESS, true);

        assertThat(informe(f)).contains("# Informe de dificultat: Parcial UT1").contains("Cap alumne ha entregat")
                .doesNotContain("## Valoració global");
    }

    @Test
    void examen_molt_facil_amb_pocs_alumnes_ho_diu_i_proposa_pujar_el_nivell() {
        FixtureExamen f = new FixtureExamen();
        Question p1 = f.pregunta(QuestionType.SHORT, "5", null);
        Question p2 = f.pregunta(QuestionType.SHORT, "5", null);
        for (String nom : new String[]{"Anna", "Biel", "Clara"}) {
            ExamSession s = f.entregat(nom, nom.toLowerCase() + "@x.cat");
            f.resposta(s, p1, "x", "5");
            f.resposta(s, p2, "y", "4.5");
        }

        String md = informe(f);

        assertThat(md).contains("**Dificultat de l'examen: Molt fàcil**")
                .contains("Poques dades")
                .contains("L'examen ha estat molt fàcil")
                .contains("Cap aprovat té indicis clars");
    }

    @Test
    void examen_molt_dificil_i_la_mitjana_per_sota_del_5_es_diuen() {
        FixtureExamen f = new FixtureExamen();
        Question p1 = f.pregunta(QuestionType.SHORT, "10", null);
        f.resposta(f.entregat("Anna", "anna@x.cat"), p1, "x", "2");
        f.resposta(f.entregat("Biel", "biel@x.cat"), p1, "x", "3");

        String md = informe(f);

        assertThat(md).contains("**Dificultat de l'examen: Molt difícil**").contains("L'examen ha estat molt difícil");
        assertThat(InformeDificultat.nivell(new java.math.BigDecimal("6.20"))).isEqualTo("Equilibrat");
        assertThat(InformeDificultat.nivell(new java.math.BigDecimal("4.50"))).isEqualTo("Difícil");
        assertThat(InformeDificultat.nivell(new java.math.BigDecimal("7.00"))).isEqualTo("Fàcil");
    }

    @Test
    void els_aprovats_amb_indicis_surten_amb_el_motiu() {
        FixtureExamen f = new FixtureExamen();
        Question test = f.test("5", "CO1");
        Question curta = f.pregunta(QuestionType.SHORT, "5", "CO2");
        f.exam.setPenalitzacioChoice(java.math.BigDecimal.ZERO);
        // Just aprovat gràcies al test: 5 + 0 = 5 sobre 10
        ExamSession aprovatPelTest = f.entregat("Pau", "pau@x.cat");
        f.resposta(aprovatPelTest, test, "b", "5");
        f.resposta(aprovatPelTest, curta, "res", "0");
        // Aprova bé
        ExamSession bo = f.entregat("Bruna", "bruna@x.cat");
        f.resposta(bo, test, "b", "5");
        f.resposta(bo, curta, "ok", "4.5");

        String md = informe(f);

        assertThat(md).contains("| Pau | 5,00 |").contains("just aprovat").contains("aprova gràcies al test")
                .contains("competències suspeses: CO2 (0,00)");
        assertThat(md).doesNotContain("| Bruna |");
    }

    @Test
    void un_aprovat_que_depen_del_bonus_i_un_amb_respostes_sense_qualificar_surten() {
        FixtureExamen f = new FixtureExamen();
        Question normal = f.pregunta(QuestionType.SHORT, "8", null);
        Question bonus = f.pregunta(QuestionType.SHORT, "2", null);
        bonus.setAnulada(true);
        ExamSession s = f.entregat("Pau", "pau@x.cat");
        f.resposta(s, normal, "x", "4");     // 4 + 2 de bonus = 6 sobre 10; sense el bonus, 4 sobre 8 = 5: no és dubtós
        ExamSession t = f.entregat("Joan", "joan@x.cat");
        f.resposta(t, normal, "x", "3");     // 3 + 2 = 5 sobre 10; sense el bonus 3/8 = 3,75
        f.resposta(f.entregat("Eva", "eva@x.cat"), normal, "x", "8");
        f.resposta(f.entregat("Pendent", "pen@x.cat"), normal, "x", null);   // sense qualificar: compta 0 però no és aprovat

        String md = informe(f);

        assertThat(md).contains("| Joan |").contains("sense les 1 preguntes de bonus la nota seria 3,75");
        assertThat(md).doesNotContain("| Eva |");
    }

    @Test
    void amb_vuit_o_mes_alumnes_es_detecten_les_preguntes_massa_facils_poc_discriminants_i_amb_clau_sospitosa() {
        FixtureExamen f = new FixtureExamen();
        Question facil = f.pregunta(QuestionType.SHORT, "2", null);       // tothom la fa bé
        Question bona = f.pregunta(QuestionType.SHORT, "6", null);        // separa bons i dolents
        Question inversa = f.pregunta(QuestionType.SHORT, "2", null);     // la fan millor els dolents
        Question test = f.test("0", null);                                // correcta b; tothom tria c
        for (int i = 0; i < 10; i++) {
            ExamSession s = f.entregat("Alumne" + i, "a" + i + "@x.cat");
            double nivell = i / 9.0;                                       // 0 .. 1
            f.resposta(s, facil, "x", "2");
            f.resposta(s, bona, "x", String.valueOf(Math.round(nivell * 6 * 100) / 100.0));
            f.resposta(s, inversa, "x", String.valueOf(Math.round((1 - nivell) * 2 * 100) / 100.0));
            f.resposta(s, test, "c", "0");
        }

        String md = informe(f);

        assertThat(md).contains("P1").contains("massa fàcil");
        assertThat(md).contains("la responen millor els alumnes amb pitjor nota");
        assertThat(md).contains("una opció incorrecta s'ha triat més que la correcta");
        assertThat(md).contains("Revisa la clau de correcció de P4");
        // la bona no surt a la llista de revisar
        assertThat(md.lines().filter(l -> l.startsWith("| P2 |"))).isEmpty();
    }

    @Test
    void el_test_gran_sense_penalitzacio_proposa_activar_la_i_les_competencies_baixes_es_nomenen() {
        FixtureExamen f = new FixtureExamen();
        Question test = f.test("6", "CO1");
        Question curta = f.pregunta(QuestionType.SHORT, "4", "CO2");
        f.exam.setCompetenciesExamen(new java.util.LinkedHashSet<>(java.util.List.of("CO1", "CO2", "CO3")));
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, test, "b", "6");
        f.resposta(s, curta, "x", "0");

        String md = informe(f);

        assertThat(md).contains("El test suma el 60 %")
                .contains("Activa la penalització");
        assertThat(md).contains("## Competències").contains("| CO3 | 0 |")
                .contains("declara CO3 però cap pregunta no l'avalua").contains("La mitjana de CO2 no arriba al 5");
    }
}
