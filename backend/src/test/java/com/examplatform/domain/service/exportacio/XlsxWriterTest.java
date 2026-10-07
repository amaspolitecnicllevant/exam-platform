package com.examplatform.domain.service.exportacio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxWriterTest {

    private static List<Object> fila(Object... v) {
        return Arrays.asList(v);
    }

    private static XlsxLector llegeix(XlsxWriter.Full... fulls) throws Exception {
        return new XlsxLector(XlsxWriter.escriu(List.of(fulls)));
    }

    @Test
    void genera_un_paquet_amb_totes_les_parts_obligatories() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("Notes", List.of("A"), List.of(fila("x"))),
                new XlsxWriter.Full("Altre", List.of("B"), List.of()));

        assertThat(x.parts.keySet()).contains("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml");
        assertThat(x.nomsDeFull()).containsExactly("Notes", "Altre");
    }

    @Test
    void escriu_text_nombres_i_deixa_buides_les_cel_les_null() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("Nom", "Nota", "Obs"),
                List.of(fila("Anna", new BigDecimal("7.5"), null), fila("Berta", 10, "ok"))));

        var c = x.cel_les(1);

        assertThat(c.get("A1").valor()).isEqualTo("Nom");
        assertThat(c.get("A2")).isEqualTo(new XlsxLector.Cel_la("text", "Anna", 0));
        assertThat(c.get("B2").tipus()).isEqualTo("nombre");
        assertThat(c.get("B2").valor()).isEqualTo("7.5");
        assertThat(c.get("B3").valor()).isEqualTo("10");
        assertThat(c).doesNotContainKey("C2");      // null: sense cel·la
        assertThat(c.get("C3").valor()).isEqualTo("ok");
        assertThat(x.files(1)).isEqualTo(3);
    }

    @Test
    void la_capcalera_porta_estil_de_negreta_i_els_nombres_format_decimal() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("Nom", "Nota"), List.of(fila("Anna", new BigDecimal("7.5")))));

        var c = x.cel_les(1);

        assertThat(c.get("A1").estil()).isEqualTo(1);
        assertThat(c.get("B2").estil()).isEqualTo(3);
        assertThat(c.get("A2").estil()).isEqualTo(0);
    }

    @Test
    void el_text_que_sembla_una_formula_es_queda_com_a_text() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("Resposta"),
                List.of(fila("=HYPERLINK(\"http://mal\")"), fila("+cmd|' /C calc'!A0"), fila("@SUM(1+1)"))));

        var c = x.cel_les(1);

        assertThat(c.get("A2")).isEqualTo(new XlsxLector.Cel_la("text", "=HYPERLINK(\"http://mal\")", 0));
        assertThat(c.get("A3").tipus()).isEqualTo("text");
        assertThat(c.get("A4").tipus()).isEqualTo("text");
        assertThat(new String(x.parts.get("xl/worksheets/sheet1.xml"))).doesNotContain("<f>");
    }

    @Test
    void escapa_els_caracters_xml_i_treu_els_de_control_que_farien_el_fitxer_il_legible() throws Exception {
        String bruta = "a & b < c > d \"e\" f\u0000g\u0008h\u000Bi￾j";

        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("T"), List.of(fila(bruta))));   // si l'XML fos invàlid, aquí fallaria

        assertThat(x.cel_les(1).get("A2").valor()).isEqualTo("a & b < c > d \"e\" fghij");
    }

    @Test
    void conserva_els_salts_de_linia_i_els_emojis() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("T"), List.of(fila("línia 1\nlínia 2\n  indentada 😀"))));

        assertThat(x.cel_les(1).get("A2").valor()).isEqualTo("línia 1\nlínia 2\n  indentada 😀");
        assertThat(new String(x.parts.get("xl/worksheets/sheet1.xml"))).contains("xml:space=\"preserve\"");
    }

    @Test
    void les_columnes_amb_text_llarg_porten_estil_d_envolta() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("A", "B"), List.of(fila("x", "y")), List.of(10, 50), Set.of(1)));

        assertThat(x.cel_les(1).get("A2").estil()).isEqualTo(0);
        assertThat(x.cel_les(1).get("B2").estil()).isEqualTo(2);
        assertThat(new String(x.parts.get("xl/worksheets/sheet1.xml"))).contains("<col min=\"2\" max=\"2\" width=\"50\"");
    }

    @Test
    void una_cel_la_massa_llarga_es_retalla_al_limit_d_excel() throws Exception {
        XlsxLector x = llegeix(new XlsxWriter.Full("F", List.of("T"), List.of(fila("x".repeat(40_000)))));

        String valor = x.cel_les(1).get("A2").valor();

        assertThat(valor.length()).isLessThanOrEqualTo(32_767).isGreaterThan(30_000);
        assertThat(valor).endsWith("…");
    }

    @Test
    void les_lletres_de_columna_segueixen_el_conveni_d_excel() {
        assertThat(XlsxWriter.columna(0)).isEqualTo("A");
        assertThat(XlsxWriter.columna(25)).isEqualTo("Z");
        assertThat(XlsxWriter.columna(26)).isEqualTo("AA");
        assertThat(XlsxWriter.columna(51)).isEqualTo("AZ");
        assertThat(XlsxWriter.columna(52)).isEqualTo("BA");
        assertThat(XlsxWriter.columna(701)).isEqualTo("ZZ");
        assertThat(XlsxWriter.columna(702)).isEqualTo("AAA");
    }

    @Test
    void molts_alumnes_i_moltes_columnes_fan_referencies_correctes() throws Exception {
        List<String> cap = new java.util.ArrayList<>();
        Object[] valors = new Object[30];
        for (int i = 0; i < 30; i++) { cap.add("c" + i); valors[i] = i; }

        XlsxLector x = llegeix(new XlsxWriter.Full("F", cap, List.of(fila(valors))));

        assertThat(x.cel_les(1).get("AD2").valor()).isEqualTo("29");   // columna 30
        assertThat(x.cel_les(1).get("AA2").valor()).isEqualTo("26");
    }

    @Test
    void els_noms_de_full_es_netegen_es_retallen_i_no_es_repeteixen() {
        var noms = XlsxWriter.nomsUnics(List.of(
                new XlsxWriter.Full("Notes: ABC/def?", List.of(), List.of()),
                new XlsxWriter.Full("x".repeat(50), List.of(), List.of()),
                new XlsxWriter.Full("NOTES: abc/def?", List.of(), List.of()),
                new XlsxWriter.Full("  ", List.of(), List.of())));

        assertThat(noms.get(0)).isEqualTo("Notes_ ABC_def_");
        assertThat(noms.get(1)).hasSize(31);
        assertThat(noms.get(2)).isNotEqualToIgnoringCase(noms.get(0)).hasSizeLessThanOrEqualTo(31);
        assertThat(noms.get(3)).isEqualTo("Full");
        assertThat(noms).doesNotHaveDuplicates();
    }

    @Test
    void sense_cap_full_es_un_error() {
        assertThatThrownBy(() -> XlsxWriter.escriu(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
