package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InformeQualificacioTest {

    private static FixtureExamen examen() {
        FixtureExamen f = new FixtureExamen();
        f.exam.setTitle("P1 · Part teòrica");
        Question test = f.test("2", "CO1");                        // correcta b
        Question curta = f.pregunta(QuestionType.SHORT, "3", "CO2");
        curta.setModelResposta("Assigna automàticament la configuració de xarxa.");
        Question llarga = f.pregunta(QuestionType.LONG, "5", "CO2");
        llarga.setModelResposta("El navegador consulta la memòria cau i el servidor DNS.");
        ExamSession anna = f.entregat("Anna Mas", "anna@x.cat");
        f.resposta(anna, test, "b", "2");
        Answer a2 = f.resposta(anna, curta, "Dona IPs", "1.5");
        a2.setComentari("Esmenta l'IP però no que és automàtic.");
        Answer a3 = f.resposta(anna, llarga, "Consulta el DNS", "4.5");
        a3.setRevisioIaJustificacio("Explica bé el procés.");
        ExamSession biel = f.entregat("Biel Pons", "biel@x.cat");
        f.resposta(biel, test, "c", "0");
        f.resposta(biel, curta, "", null);
        f.alumne("Clara Sans", "clara@x.cat", SessionStatus.IN_PROGRESS, true);
        return f;
    }

    @Test
    void segueix_l_estructura_de_la_sintaxi_dels_informes_de_practiques() {
        String md = InformeQualificacio.informe(examen().dades());

        assertThat(md).startsWith("# Informes de qualificació — P1 · Part teòrica\n");
        assertThat(md).contains("Nota final = 100% teoria. Teoria: 3 preguntes, 10 punts en total.");
        assertThat(md).contains("## Resum de la classe\n\n| Alumne | Teoria | Final |\n|---|---:|---:|\n");
        assertThat(md).contains("\n---\n\n## Anna Mas\n\n### Resum\n\n| Part | Nota (sobre 10) | Pes |\n|---|---:|---:|\n");
        assertThat(md).contains("| Part teòrica | 8 | 100% |").contains("| **Nota final** | **8** | |")
                .contains("*Càlcul: 100% × 8 = 8*");
        assertThat(md).contains("### Part teòrica").contains("### Què pots millorar");
        assertThat(md).contains("| Pregunta | Nota | Per què | Resposta esperada |\n|---|---:|---|---|\n");
    }

    @Test
    void el_resum_de_la_classe_te_una_fila_per_alumne_i_els_no_entregats_surten_com_a_tals() {
        String md = InformeQualificacio.informe(examen().dades());

        assertThat(md).contains("| Anna Mas | 8 | 8 |").contains("| Biel Pons | 0 | 0 |")
                .contains("| Clara Sans | No entregada | — |");
        assertThat(md).contains("## Clara Sans\n\n### Resum\n\n| Part | Nota (sobre 10) | Pes |\n|---|---:|---:|\n| Part teòrica | No entregada | 100% |\n| **Nota final** | **—** | |");
    }

    @Test
    void cada_pregunta_diu_d_on_surt_la_nota_amb_el_comentari_la_revisio_o_la_clau() {
        String md = InformeQualificacio.informe(examen().dades());

        // Test encertat: nota completa, sense resposta esperada
        assertThat(md).contains("| 1. Enunciat 1 | 2 / 2 | Resposta correcta. | — |");
        // Curta: comentari del professor; la resposta esperada surt perquè està per sota del 90 %
        assertThat(md).contains("| 2. Enunciat 2 | 1,5 / 3 | Esmenta l'IP però no que és automàtic. | Assigna automàticament la configuració de xarxa. |");
        // Llarga: justificació de la revisió; 4,5 de 5 és el 90 %, així que no porta resposta esperada
        assertThat(md).contains("| 3. Enunciat 3 | 4,5 / 5 | Explica bé el procés. | — |");
        // Biel: test errat i curta sense contestar
        assertThat(md).contains("| 1. Enunciat 1 | 0 / 2 | Ha triat c; la correcta és b. | b) Segona |");
        assertThat(md).contains("| 2. Enunciat 2 | 0 / 3 | No contestada. | Assigna automàticament la configuració de xarxa. |");
    }

    @Test
    void que_pots_millorar_llista_les_preguntes_per_sota_del_60_per_cent() {
        String md = InformeQualificacio.informe(examen().dades());

        String biel = md.substring(md.indexOf("## Biel Pons"), md.indexOf("## Clara Sans"));
        assertThat(biel).contains("- **Teoria 1. Enunciat 1** (0/2): b) Segona")
                .contains("- **Teoria 2. Enunciat 2** (0/3): Assigna automàticament la configuració de xarxa.");
        String anna = md.substring(md.indexOf("## Anna Mas"), md.indexOf("## Biel Pons"));
        assertThat(anna).contains("- **Teoria 2. Enunciat 2** (1,5/3): Assigna automàticament la configuració de xarxa.")
                .doesNotContain("Teoria 1.").doesNotContain("Teoria 3.");
    }

    @Test
    void si_cap_pregunta_esta_per_sota_del_60_per_cent_ho_diu() {
        FixtureExamen f = new FixtureExamen();
        Question q = f.pregunta(QuestionType.SHORT, "10", null);
        f.resposta(f.entregat("Anna", "anna@x.cat"), q, "x", "9");

        assertThat(InformeQualificacio.informe(f.dades()))
                .contains("### Què pots millorar\n\n- Cap pregunta per sota del 60 % dels punts.");
    }

    @Test
    void explica_la_penalitzacio_del_test_i_el_bonus_i_neteja_les_cel_les_de_la_taula() {
        FixtureExamen f = new FixtureExamen();
        f.exam.setPenalitzacioChoice(new BigDecimal("0.3333"));
        Question test = f.test("1", null);
        Question bonus = f.pregunta(QuestionType.SHORT, "1", null);
        bonus.setAnulada(true);
        Question curta = f.pregunta(QuestionType.SHORT, "8", null);
        curta.setEnunciat("Explica | amb\nsalt de línia");
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, test, "a", "-0.33");
        f.resposta(s, curta, "x", "4").setComentari("Falta |detall|.");

        String md = InformeQualificacio.informe(f.dades());

        assertThat(md).contains("Les respostes errades del test resten 33,33 % dels punts de la pregunta; les en blanc no resten.")
                .contains("Les preguntes anul·lades donen els punts a tothom.");
        assertThat(md).contains("| 2. Enunciat 2 | 1 / 1 | Pregunta anul·lada: tothom té els punts. | — |");
        assertThat(md).contains("| 3. Explica \\| amb salt de línia | 4 / 8 | Falta \\|detall\\|. |");
        assertThat(md).contains("| 1. Enunciat 1 | -0,33 / 1 |");
    }

    @Test
    void una_resposta_pendent_de_revisar_ho_diu_i_compta_zero() {
        FixtureExamen f = new FixtureExamen();
        Question q = f.pregunta(QuestionType.SHORT, "10", null);
        f.resposta(f.entregat("Anna", "anna@x.cat"), q, "x", null);

        String md = InformeQualificacio.informe(f.dades());

        assertThat(md).contains("| 1. Enunciat 1 | 0 / 10 | Pendent de revisar: de moment compta 0. |");
    }

    @Test
    void amb_seccions_la_nota_final_es_reparteix_per_parts_segons_els_seus_punts() {
        FixtureExamen f = new FixtureExamen();
        f.seccio("Part 1 — Teoria");
        Question t1 = f.pregunta(QuestionType.SHORT, "4", "CO1");
        f.seccio("Part 2 — Pràctica");
        Question p1 = f.pregunta(QuestionType.SHORT, "3", "CO2");
        Question p2 = f.pregunta(QuestionType.SHORT, "3", "CO2");
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, t1, "x", "3");       // 7,5 sobre 10 a la part 1
        f.resposta(s, p1, "x", "3");       // 8 sobre 10 a la part 2: (3 + 2) / 6
        f.resposta(s, p2, "x", "2");

        String md = InformeQualificacio.informe(f.dades());

        assertThat(md).contains("Nota final = 40% Part 1 — Teoria + 60% Part 2 — Pràctica. Cada part pesa segons els seus punts");
        assertThat(md).contains("| Part 1 — Teoria | 7,5 | 40% |").contains("| Part 2 — Pràctica | 8,33 | 60% |")
                .contains("| **Nota final** | **8** | |");
        assertThat(md).contains("*Càlcul: 40% × 7,5 + 60% × 8,33 = 8*");
        assertThat(md).contains("### Part 1 — Teoria\n\n1 pregunta, 4 punts (40% de la nota).")
                .contains("### Part 2 — Pràctica\n\n2 preguntes, 6 punts (60% de la nota).");
        // La numeració de les preguntes no compta les seccions i continua entre parts
        assertThat(md).contains("| 1. Enunciat 2 |").contains("| 2. Enunciat 4 |").contains("| 3. Enunciat 5 |");
        assertThat(md).doesNotContain("### Part teòrica");
    }

    @Test
    void la_taula_per_competencia_diu_d_on_surt_cada_competencia() {
        FixtureExamen f = new FixtureExamen();
        Question a = f.pregunta(QuestionType.SHORT, "4", "CO1");
        Question b = f.pregunta(QuestionType.SHORT, "6", "CO2");
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, a, "x", "4");
        f.resposta(s, b, "x", "3");

        String md = InformeQualificacio.informe(f.dades());

        assertThat(md).contains("### Per competència\n\n| Competència | Punts | Nota (sobre 10) |\n|---|---:|---:|\n")
                .contains("| CO1 | 4 / 4 | 10 |").contains("| CO2 | 3 / 6 | 5 |");
    }

    @Test
    void sense_competencies_no_hi_ha_taula_per_competencia() {
        FixtureExamen f = new FixtureExamen();
        f.resposta(f.entregat("Anna", "anna@x.cat"), f.pregunta(QuestionType.SHORT, "10", null), "x", "5");

        assertThat(InformeQualificacio.informe(f.dades())).doesNotContain("Per competència");
    }

    @Test
    void sense_alumnes_nomes_porta_la_capcalera_i_el_resum_buit() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "10", null);

        String md = InformeQualificacio.informe(f.dades());

        assertThat(md).contains("## Resum de la classe").doesNotContain("### Resum");
    }
}
