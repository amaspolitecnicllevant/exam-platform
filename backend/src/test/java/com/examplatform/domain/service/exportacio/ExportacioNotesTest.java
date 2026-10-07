package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportacioNotesTest {

    private static List<String> linies(String csv) {
        return Arrays.asList(csv.replace("\uFEFF", "").split("\r?\n"));
    }

    private static String[] cel_les(String linia) {
        return linia.split(";", -1);
    }

    // ── notes per alumne ──────────────────────────────────────────────────────

    @Test
    void notes_porta_BOM_separador_punt_i_coma_i_capcalera_de_preguntes() {
        FixtureExamen f = new FixtureExamen();
        f.test("4", "RA1");
        f.pregunta(QuestionType.SHORT, "6", "RA2");
        f.resposta(f.entregat("Anna", "anna@x.cat"), f.exam.getQuestions().get(0), "b", "4");

        String csv = ExportacioNotes.notesCsv(f.dades());

        assertThat(csv).startsWith("\uFEFF");
        assertThat(linies(csv).get(0))
                .isEqualTo("alumne;email;estat;nota_sobre_10;punts;punts_maxims;pendents_de_revisar;P1 (4 pts);P2 (6 pts)");
    }

    @Test
    void una_fila_per_alumne_ordenats_per_nom_amb_nota_sobre_10_amb_coma_decimal() {
        FixtureExamen f = new FixtureExamen();
        Question p1 = f.pregunta(QuestionType.SHORT, "4", null);
        Question p2 = f.pregunta(QuestionType.SHORT, "6", null);
        var berta = f.entregat("Berta", "berta@x.cat");
        var anna = f.entregat("anna", "anna@x.cat");
        f.resposta(anna, p1, "x", "3");
        f.resposta(anna, p2, "y", "4.5");
        f.resposta(berta, p1, "x", "4");
        f.resposta(berta, p2, "y", "6");

        List<String> l = linies(ExportacioNotes.notesCsv(f.dades()));

        assertThat(l).hasSize(3);
        assertThat(cel_les(l.get(1))).startsWith("anna", "anna@x.cat", "Entregat", "7,50", "7,50", "10,00", "0", "3,00", "4,50");
        assertThat(cel_les(l.get(2))).startsWith("Berta", "berta@x.cat", "Entregat", "10,00", "10,00");
    }

    @Test
    void estats_entregat_en_curs_i_no_presentat_i_el_no_presentat_no_porta_nota() {
        FixtureExamen f = new FixtureExamen();
        Question p1 = f.pregunta(QuestionType.SHORT, "10", null);
        f.resposta(f.entregat("A Entregat", "a@x.cat"), p1, "x", "10");
        f.alumne("B EnCurs", "b@x.cat", SessionStatus.IN_PROGRESS, true);
        f.alumne("C Absent", "c@x.cat", SessionStatus.IN_PROGRESS, false);

        List<String> l = linies(ExportacioNotes.notesCsv(f.dades()));

        assertThat(cel_les(l.get(1))[2]).isEqualTo("Entregat");
        assertThat(cel_les(l.get(2))[2]).isEqualTo("En curs");
        assertThat(cel_les(l.get(3))[2]).isEqualTo("No presentat");
        String[] absent = cel_les(l.get(3));
        assertThat(absent[3]).isEmpty();      // nota
        assertThat(absent[4]).isEmpty();      // punts
        assertThat(absent[6]).isEmpty();      // pendents
        assertThat(absent[7]).isEmpty();      // pregunta
        assertThat(absent[5]).isEqualTo("10,00");   // els punts màxims sí
    }

    @Test
    void una_resposta_sense_qualificar_surt_com_pendent_i_es_compta() {
        FixtureExamen f = new FixtureExamen();
        Question p1 = f.pregunta(QuestionType.FILE_UPLOAD, "5", null);
        Question p2 = f.pregunta(QuestionType.SHORT, "5", null);
        var s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p1, "Treball.docx", null);
        f.resposta(s, p2, "x", "5");

        String[] fila = cel_les(linies(ExportacioNotes.notesCsv(f.dades())).get(1));

        assertThat(fila[6]).isEqualTo("1");           // pendents
        assertThat(fila[7]).isEqualTo("pendent");     // P1
        assertThat(fila[8]).isEqualTo("5,00");        // P2
        assertThat(fila[3]).isEqualTo("5,00");        // provisional: el pendent compta 0
    }

    @Test
    void una_pregunta_sense_resposta_compta_zero_i_una_anullada_compta_els_punts_a_tothom() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "5", null);
        Question bonus = f.pregunta(QuestionType.SHORT, "5", null);
        bonus.setAnulada(true);
        f.entregat("Anna", "anna@x.cat");

        String[] fila = cel_les(linies(ExportacioNotes.notesCsv(f.dades())).get(1));

        assertThat(fila[7]).isEqualTo("0,00");
        assertThat(fila[8]).isEqualTo("5,00");
        assertThat(fila[3]).isEqualTo("5,00");
    }

    @Test
    void els_noms_amb_formules_es_netegen_i_els_que_porten_punt_i_coma_es_cometen() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "10", null);
        f.entregat("=HYPERLINK(\"http://mal\")", "+34@x.cat");
        f.entregat("Pere; el \"Gran\"", "p@x.cat");

        String csv = ExportacioNotes.notesCsv(f.dades());

        assertThat(csv).contains("'=HYPERLINK").contains("'+34@x.cat");
        assertThat(csv).contains("\"Pere; el \"\"Gran\"\"\"");
        assertThat(csv).doesNotContain(";=HYPERLINK").doesNotContain("\n=HYPERLINK");
    }

    @Test
    void examen_sense_alumnes_nomes_porta_la_capcalera() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "10", null);

        assertThat(linies(ExportacioNotes.notesCsv(f.dades()))).hasSize(1);
    }

    // ── notes per RA ──────────────────────────────────────────────────────────

    @Test
    void ra_en_ordre_natural_i_sense_ra_al_final() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "2", "RA10");
        f.pregunta(QuestionType.SHORT, "2", "RA2");
        f.pregunta(QuestionType.SHORT, "2", "RA1");
        f.pregunta(QuestionType.SHORT, "2", null);
        f.pregunta(QuestionType.SHORT, "2", "  ");

        assertThat(ExportacioNotes.resultatsAprenentatge(f.dades()))
                .containsExactly("RA1", "RA2", "RA10", ExportacioNotes.SENSE_RA);
    }

    @Test
    void la_nota_per_ra_es_sobre_10_amb_els_punts_d_aquell_ra() {
        FixtureExamen f = new FixtureExamen();
        Question a1 = f.pregunta(QuestionType.SHORT, "2", "RA1");
        Question a2 = f.pregunta(QuestionType.SHORT, "2", "RA1");
        Question b1 = f.pregunta(QuestionType.SHORT, "6", "RA2");
        var s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, a1, "x", "2");
        f.resposta(s, a2, "x", "1");     // RA1: 3 de 4 → 7,5
        f.resposta(s, b1, "x", "6");     // RA2: 6 de 6 → 10

        List<String> l = linies(ExportacioNotes.raCsv(f.dades()));

        assertThat(l.get(0)).isEqualTo("alumne;email;RA1;RA2;nota_global");
        assertThat(l.get(1)).isEqualTo("Anna;anna@x.cat;7,50;10,00;9,00");
    }

    @Test
    void les_notes_per_ra_nomes_inclouen_alumnes_entregats() {
        FixtureExamen f = new FixtureExamen();
        Question p = f.pregunta(QuestionType.SHORT, "10", "RA1");
        f.resposta(f.entregat("Anna", "anna@x.cat"), p, "x", "10");
        f.alumne("Absent", "b@x.cat", SessionStatus.IN_PROGRESS, false);
        f.alumne("EnCurs", "c@x.cat", SessionStatus.IN_PROGRESS, true);

        assertThat(linies(ExportacioNotes.raCsv(f.dades()))).hasSize(2);
    }

    @Test
    void una_resposta_sense_qualificar_compta_zero_al_seu_ra() {
        FixtureExamen f = new FixtureExamen();
        Question p = f.pregunta(QuestionType.FILE_UPLOAD, "10", "RA1");
        f.resposta(f.entregat("Anna", "anna@x.cat"), p, "x.docx", null);

        assertThat(linies(ExportacioNotes.raCsv(f.dades())).get(1)).isEqualTo("Anna;anna@x.cat;0,00;0,00");
    }
}
