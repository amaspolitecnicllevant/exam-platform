package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportacioExcelTest {

    private static FixtureExamen examen() {
        FixtureExamen f = new FixtureExamen();
        Question test = f.test("4", "RA1");
        Question curta = f.pregunta(QuestionType.SHORT, "3", "RA2");
        Question fitxer = f.pregunta(QuestionType.FILE_UPLOAD, "3", "RA2");
        var anna = f.entregat("Anna Soler", "anna@x.cat");
        f.resposta(anna, test, "b", "4");
        f.resposta(anna, curta, "=1+1\nsegona línia", "2.5");
        Answer a = f.resposta(anna, fitxer, "Treball.docx", null);
        a.setFitxerNom("Treball.docx");
        a.setFitxerRuta("/x/y.docx");
        f.alumne("Berta Absent", "berta@x.cat", SessionStatus.IN_PROGRESS, false);
        return f;
    }

    @Test
    void te_tres_fulls_notes_notes_per_ra_i_respostes() throws Exception {
        XlsxLector x = new XlsxLector(ExportacioExcel.excel(examen().dades()));

        assertThat(x.nomsDeFull()).containsExactly("Notes", "Notes per RA", "Respostes");
    }

    @Test
    void el_full_de_notes_porta_nombres_i_no_text_per_a_les_notes() throws Exception {
        XlsxLector x = new XlsxLector(ExportacioExcel.excel(examen().dades()));
        var c = x.cel_les(1);

        assertThat(c.get("A1").valor()).isEqualTo("Alumne");
        assertThat(c.get("H1").valor()).isEqualTo("P1 (4 pts)");
        assertThat(c.get("A2").valor()).isEqualTo("Anna Soler");
        assertThat(c.get("C2").valor()).isEqualTo("Entregat");
        assertThat(c.get("D2")).isEqualTo(new XlsxLector.Cel_la("nombre", "6.50", 3));   // nota sobre 10: 6,5 de 10
        assertThat(c.get("E2").valor()).isEqualTo("6.50");                                 // punts
        assertThat(c.get("F2").valor()).isEqualTo("10.00");                                // màxim
        assertThat(c.get("G2").valor()).isEqualTo("1");                                    // pendents
        assertThat(c.get("H2").valor()).isEqualTo("4.00");
        assertThat(c.get("I2").valor()).isEqualTo("2.50");
        assertThat(c.get("J2")).isEqualTo(new XlsxLector.Cel_la("text", "pendent", 0));
    }

    @Test
    void l_alumne_no_presentat_no_porta_nota_ni_punts_pero_si_el_maxim() throws Exception {
        var c = new XlsxLector(ExportacioExcel.excel(examen().dades())).cel_les(1);

        assertThat(c.get("A3").valor()).isEqualTo("Berta Absent");
        assertThat(c.get("C3").valor()).isEqualTo("No presentat");
        assertThat(c).doesNotContainKeys("D3", "E3", "G3", "H3");
        assertThat(c.get("F3").valor()).isEqualTo("10.00");
    }

    @Test
    void notes_per_ra_nomes_te_els_entregats_amb_una_columna_per_ra() throws Exception {
        XlsxLector x = new XlsxLector(ExportacioExcel.excel(examen().dades()));
        var c = x.cel_les(2);

        assertThat(x.files(2)).isEqualTo(2);
        assertThat(c.get("C1").valor()).isEqualTo("RA1");
        assertThat(c.get("D1").valor()).isEqualTo("RA2");
        assertThat(c.get("E1").valor()).isEqualTo("Nota global");
        assertThat(c.get("C2").valor()).isEqualTo("10.00");   // RA1: 4 de 4
        assertThat(c.get("D2").valor()).isEqualTo("4.17");    // RA2: 2,5 de 6 → 4,17
    }

    @Test
    void el_full_de_respostes_te_una_fila_per_alumne_entregat_i_pregunta() throws Exception {
        XlsxLector x = new XlsxLector(ExportacioExcel.excel(examen().dades()));
        var c = x.cel_les(3);

        assertThat(x.files(3)).isEqualTo(4);                  // capçalera + 3 preguntes de l'únic entregat
        assertThat(c.get("C3").valor()).isEqualTo("P2");
        assertThat(c.get("F3").valor()).isEqualTo("=1+1\nsegona línia");
        assertThat(c.get("F3").tipus()).isEqualTo("text");     // mai s'interpreta com a fórmula
        assertThat(c.get("F4").valor()).isEqualTo("Fitxer: Treball.docx");
        assertThat(c.get("G4").valor()).isEqualTo("pendent");
    }

    @Test
    void un_examen_buit_genera_igualment_un_excel_valid() throws Exception {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "10", null);

        XlsxLector x = new XlsxLector(ExportacioExcel.excel(f.dades()));

        assertThat(x.nomsDeFull()).hasSize(3);
        assertThat(x.files(1)).isEqualTo(1);
    }
}
