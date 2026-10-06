package com.examplatform.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatsFitxerTest {

    private static boolean valida(String ext, byte... bytes) {
        return FormatsFitxer.signaturaValida(ext, bytes, bytes.length);
    }

    private static boolean valida(String ext, String text) {
        byte[] b = text.getBytes(StandardCharsets.ISO_8859_1);
        return FormatsFitxer.signaturaValida(ext, b, b.length);
    }

    // ── llista de formats permesos ────────────────────────────────────────────

    @Test
    void admet_word_excel_i_packet_tracer() {
        assertThat(FormatsFitxer.permesos()).contains("docx", "xlsx", "pkt", "pka", "pkz", "pdf");
    }

    @Test
    void no_admet_documents_amb_macros_ni_executables() {
        assertThat(FormatsFitxer.permesos())
                .doesNotContain("docm", "xlsm", "pptm", "exe", "bat", "sh", "js", "html", "svg", "jar", "dll", "ps1");
    }

    // ── extensió ──────────────────────────────────────────────────────────────

    @Test
    void extensio_en_minuscules_sense_punt() {
        assertThat(FormatsFitxer.extensio("Informe.DOCX")).isEqualTo("docx");
        assertThat(FormatsFitxer.extensio("xarxa.pkt")).isEqualTo("pkt");
    }

    @Test
    void extensio_es_la_darrera() {
        assertThat(FormatsFitxer.extensio("treball.docx.exe")).isEqualTo("exe");
        assertThat(FormatsFitxer.extensio("a.b.c.xlsx")).isEqualTo("xlsx");
    }

    @Test
    void extensio_buida_si_no_n_hi_ha() {
        assertThat(FormatsFitxer.extensio("sense")).isEmpty();
        assertThat(FormatsFitxer.extensio("acaba.")).isEmpty();
        assertThat(FormatsFitxer.extensio(null)).isEmpty();
    }

    // ── llista escrita pel professor ──────────────────────────────────────────

    @Test
    void normalitzaLlista_acepta_majuscules_espais_i_punt_inicial() {
        assertThat(FormatsFitxer.normalitzaLlista(" DOCX, .xlsx ,pkt")).containsExactly("docx", "xlsx", "pkt");
    }

    @Test
    void normalitzaLlista_treu_duplicats_i_conserva_l_ordre() {
        assertThat(FormatsFitxer.normalitzaLlista("pkt,docx,PKT,docx")).containsExactly("pkt", "docx");
    }

    @Test
    void normalitzaLlista_rebutja_format_desconegut_i_el_nomena() {
        assertThatThrownBy(() -> FormatsFitxer.normalitzaLlista("docx,exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("«exe»")
                .hasMessageContaining("Formats admesos");
    }

    @Test
    void normalitzaLlista_rebutja_macros() {
        assertThatThrownBy(() -> FormatsFitxer.normalitzaLlista("xlsm"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("«xlsm»");
    }

    @Test
    void normalitzaLlista_rebutja_llista_buida() {
        assertThatThrownBy(() -> FormatsFitxer.normalitzaLlista("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FormatsFitxer.normalitzaLlista(" , ,")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FormatsFitxer.normalitzaLlista(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deLaPregunta_sense_llista_son_tots_els_permesos() {
        assertThat(FormatsFitxer.deLaPregunta(null)).isEqualTo(FormatsFitxer.permesos());
        assertThat(FormatsFitxer.deLaPregunta("  ")).isEqualTo(FormatsFitxer.permesos());
        assertThat(FormatsFitxer.deLaPregunta("docx,pkt")).isEqualTo(List.of("docx", "pkt"));
    }

    // ── signatures ────────────────────────────────────────────────────────────

    @Test
    void zip_office_ha_de_comencar_per_PK() {
        assertThat(valida("docx", (byte) 'P', (byte) 'K', (byte) 3, (byte) 4, (byte) 0)).isTrue();
        assertThat(valida("xlsx", (byte) 'P', (byte) 'K', (byte) 3, (byte) 4)).isTrue();
        assertThat(valida("docx", "Hola, això és text pla")).isFalse();
        assertThat(valida("docx", (byte) 'P', (byte) 'K')).isFalse();               // massa curt
        assertThat(valida("odt", (byte) 'M', (byte) 'Z', (byte) 0, (byte) 0)).isFalse();   // executable de Windows
    }

    @Test
    void pdf_ha_de_contenir_la_signatura_al_principi() {
        assertThat(valida("pdf", "%PDF-1.7\n...")).isTrue();
        assertThat(valida("pdf", "\n\n%PDF-1.4")).isTrue();                          // text abans de la signatura
        assertThat(valida("pdf", "<html>no sóc un pdf</html>")).isFalse();
    }

    @Test
    void imatges_han_de_tenir_la_seva_signatura() {
        assertThat(valida("png", (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', (byte) 13, (byte) 10, (byte) 26, (byte) 10)).isTrue();
        assertThat(valida("png", (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0)).isFalse();
        assertThat(valida("jpg", (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0)).isTrue();
        assertThat(valida("jpeg", (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)).isTrue();
        assertThat(valida("jpg", "text")).isFalse();
    }

    @Test
    void packet_tracer_no_te_signatura_fiable_i_nomes_es_comprova_l_extensio() {
        assertThat(valida("pkt", (byte) 1, (byte) 2, (byte) 3)).isTrue();
        assertThat(valida("pka", (byte) 0)).isTrue();
        assertThat(valida("pkz", "qualsevol cosa")).isTrue();
    }

    @Test
    void extensio_no_permesa_mai_es_valida() {
        assertThat(valida("exe", (byte) 'M', (byte) 'Z')).isFalse();
        assertThat(valida("docm", (byte) 'P', (byte) 'K', (byte) 3, (byte) 4)).isFalse();
    }
}
