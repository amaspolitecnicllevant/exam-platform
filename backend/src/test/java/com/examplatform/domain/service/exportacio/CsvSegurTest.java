package com.examplatform.domain.service.exportacio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CsvSegurTest {

    @Test
    void cel_neteja_les_formules_i_els_espais() {
        assertThat(CsvSegur.cel("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvSegur.cel("+34 600")).isEqualTo("'+34 600");
        assertThat(CsvSegur.cel("-5")).isEqualTo("'-5");
        assertThat(CsvSegur.cel("@usuari")).isEqualTo("'@usuari");
        assertThat(CsvSegur.cel("\tx")).isEqualTo("x");           // el strip treu el tabulador inicial
        assertThat(CsvSegur.cel("  normal  ")).isEqualTo("normal");
        assertThat(CsvSegur.cel(null)).isEmpty();
        assertThat(CsvSegur.cel("a=b")).isEqualTo("a=b");
    }

    @Test
    void num_porta_dues_xifres_i_coma_decimal() {
        assertThat(CsvSegur.num(new BigDecimal("7.5"))).isEqualTo("7,50");
        assertThat(CsvSegur.num(new BigDecimal("10"))).isEqualTo("10,00");
        assertThat(CsvSegur.num(new BigDecimal("6.666"))).isEqualTo("6,67");
        assertThat(CsvSegur.num(new BigDecimal("-0.25"))).isEqualTo("-0,25");
        assertThat(CsvSegur.num(null)).isEmpty();
    }

    @Test
    void curt_treu_els_zeros_sobrers() {
        assertThat(CsvSegur.curt(new BigDecimal("2"))).isEqualTo("2");
        assertThat(CsvSegur.curt(new BigDecimal("2.00"))).isEqualTo("2");
        assertThat(CsvSegur.curt(new BigDecimal("2.5"))).isEqualTo("2,5");
        assertThat(CsvSegur.curt(new BigDecimal("0.25"))).isEqualTo("0,25");
        assertThat(CsvSegur.curt(new BigDecimal("10"))).isEqualTo("10");
        assertThat(CsvSegur.curt(new BigDecimal("100"))).isEqualTo("100");
        assertThat(CsvSegur.curt(new BigDecimal("33.333"))).isEqualTo("33,33");
        assertThat(CsvSegur.curt(BigDecimal.ZERO)).isEqualTo("0");
        assertThat(CsvSegur.curt(null)).isEmpty();
    }

    @Test
    void la_capcalera_de_pregunta_no_afegeix_zeros_als_punts_decimals() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(com.examplatform.domain.model.QuestionType.SHORT, "2.5", null);
        f.pregunta(com.examplatform.domain.model.QuestionType.SHORT, "2", null);

        assertThat(ExportacioNotes.capcaleraPregunta(f.dades().preguntes().get(0))).isEqualTo("P1 (2,5 pts)");
        assertThat(ExportacioNotes.capcaleraPregunta(f.dades().preguntes().get(1))).isEqualTo("P2 (2 pts)");
    }
}
