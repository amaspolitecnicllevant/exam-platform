package com.examplatform.domain.service;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PenalitzacioInstruccionsTest {

    private static Question test(String punts) {
        return Question.builder().id(UUID.randomUUID()).tipus(QuestionType.CHOICE).punts(new BigDecimal(punts)).build();
    }

    private static Question curta(String punts) {
        return Question.builder().id(UUID.randomUUID()).tipus(QuestionType.SHORT).punts(new BigDecimal(punts)).build();
    }

    private static final List<Question> TEST_0_10 = List.of(test("0.10"), test("0.10"), curta("0.80"));

    @Test
    void fraccio_explicita_a_les_instruccions() {
        String t = "Part A — Test (10 preguntes, 0,70 pts cadascuna): selecciona la resposta correcta. Resposta incorrecta: "
                + "−0,23 pts (1/3 de la pregunta); en blanc: 0 pts. Part B — Pregunta curta (3 pts): respon amb precisió; "
                + "cada concepte clau que falti resta 0,75 pts.";

        assertThat(PenalitzacioInstruccions.detecta(t, List.of(test("0.70")))).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("0.3333"));
    }

    @Test
    void punts_dividits_pels_punts_de_la_pregunta_i_arrodonits_a_una_fraccio_habitual() {
        String t = "Part A — Test (30 preguntes, 0,10 pts cadascuna): selecciona la resposta correcta. Resposta incorrecta: "
                + "−0,03 pts sobre la nota final; en blanc: 0 pts. Part B — Preguntes curtes (5 × 0,80 pts): respon amb precisió.";

        assertThat(PenalitzacioInstruccions.detecta(t, TEST_0_10)).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("0.3333"));
    }

    @Test
    void punts_que_no_s_assemblen_a_cap_fraccio_habitual_es_deixen_tal_qual() {
        assertThat(PenalitzacioInstruccions.detecta("Cada resposta incorrecta resta 0,4 pts.", List.of(test("1"))))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.4"));
    }

    @Test
    void percentatge() {
        assertThat(PenalitzacioInstruccions.detecta("Les respostes errades penalitzen un 25% del valor.", List.of(test("1"))))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.25"));
    }

    @Test
    void fraccio_amb_punt_decimal_a_la_frase_anterior_no_trenca_les_frases() {
        String t = "Test de 0.5 pts cadascuna. Una resposta incorrecta resta 1/4 de la pregunta.";

        assertThat(PenalitzacioInstruccions.detecta(t, List.of(test("0.5")))).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("0.25"));
    }

    @Test
    void si_diuen_que_no_resten_no_es_dedueix_res() {
        assertThat(PenalitzacioInstruccions.detecta(
                "Llegeix cada pregunta atentament. A la Part 1, una sola resposta és correcta; les incorrectes no resten. "
                        + "Les respostes en blanc puntuen 0.", TEST_0_10)).isEmpty();
        assertThat(PenalitzacioInstruccions.detecta("Sense penalització per errors (1/3 no s'aplica).", TEST_0_10)).isEmpty();
    }

    @Test
    void una_resta_que_no_parla_d_errors_no_compta() {
        assertThat(PenalitzacioInstruccions.detecta("Cada concepte clau que falti resta 0,75 pts.", List.of(test("0.1")))).isEmpty();
    }

    @Test
    void en_punts_amb_preguntes_de_test_de_valors_diferents_no_es_pot_deduir() {
        assertThat(PenalitzacioInstruccions.detecta("Resposta incorrecta: −0,05 pts.", List.of(test("0.1"), test("0.2")))).isEmpty();
        assertThat(PenalitzacioInstruccions.detecta("Resposta incorrecta: −0,05 pts.", List.of(curta("1")))).isEmpty();
    }

    @Test
    void valors_fora_de_rang_s_ignoren() {
        assertThat(PenalitzacioInstruccions.detecta("Una resposta incorrecta resta 3/2 de la pregunta.", TEST_0_10)).isEmpty();
        assertThat(PenalitzacioInstruccions.detecta("Una resposta incorrecta resta 1/0.", TEST_0_10)).isEmpty();
        assertThat(PenalitzacioInstruccions.detecta("Errades: 150%.", TEST_0_10)).isEmpty();
    }

    @Test
    void sense_instruccions_no_es_dedueix_res() {
        assertThat(PenalitzacioInstruccions.detecta(null, TEST_0_10)).isEmpty();
        assertThat(PenalitzacioInstruccions.detecta("  ", TEST_0_10)).isEmpty();
    }
}
