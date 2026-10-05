package com.examplatform.domain.service;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PuntuacioTest {

    private static Question pregunta(QuestionType tipus, String punts) {
        return Question.builder().id(UUID.randomUUID()).tipus(tipus).punts(new BigDecimal(punts)).build();
    }

    private static Answer resposta(String auto, String manual) {
        return Answer.builder().id(UUID.randomUUID())
                .autoScore(auto == null ? null : new BigDecimal(auto))
                .manualScore(manual == null ? null : new BigDecimal(manual)).build();
    }

    @Test
    void la_nota_revisada_te_prioritat_sobre_la_proposta() {
        assertThat(Puntuacio.punts(pregunta(QuestionType.SHORT, "2"), resposta("1.5", "1"))).isEqualByComparingTo("1");
    }

    @Test
    void sense_nota_revisada_compta_la_proposta() {
        assertThat(Puntuacio.punts(pregunta(QuestionType.CHOICE, "1"), resposta("-0.25", null))).isEqualByComparingTo("-0.25");
    }

    @Test
    void resposta_sense_cap_nota_es_pendent() {
        assertThat(Puntuacio.punts(pregunta(QuestionType.SHORT, "2"), resposta(null, null))).isNull();
    }

    @Test
    void sense_resposta_val_zero() {
        assertThat(Puntuacio.punts(pregunta(QuestionType.SHORT, "2"), null)).isEqualByComparingTo("0");
    }

    @Test
    void bonus_dona_els_punts_sencers_a_tothom() {
        Question q = pregunta(QuestionType.CHOICE, "2");
        q.setAnulada(true);

        assertThat(Puntuacio.punts(q, resposta("-0.5", null))).isEqualByComparingTo("2");   // l'havia fallada
        assertThat(Puntuacio.punts(q, resposta("0", "0"))).isEqualByComparingTo("2");        // puntuada a mà
        assertThat(Puntuacio.punts(q, null)).isEqualByComparingTo("2");                      // no l'havia resposta
    }

    @Test
    void total_suma_cada_pregunta_una_sola_vegada() {
        Question p1 = pregunta(QuestionType.SHORT, "5");
        Question p2 = pregunta(QuestionType.CHOICE, "3");
        Question p3 = pregunta(QuestionType.SHORT, "2");
        p3.setAnulada(true);
        Answer r1 = resposta("4", "4.5");      // proposta i nota revisada: compta 4,5 (no 8,5)
        Answer r2 = resposta("3", null);

        BigDecimal total = Puntuacio.total(List.of(p1, p2, p3), Map.of(p1.getId(), r1, p2.getId(), r2));

        assertThat(total).isEqualByComparingTo("9.5");   // 4,5 + 3 + 2 (bonus sense resposta)
    }

    @Test
    void les_seccions_no_compten() {
        assertThat(Puntuacio.punts(pregunta(QuestionType.SECTION, "0"), null)).isEqualByComparingTo("0");
    }

    @Test
    void nota_sobre_10_no_compta_les_seccions() {
        Question seccio = pregunta(QuestionType.SECTION, "0");
        Question p1 = pregunta(QuestionType.SHORT, "15");
        Question p2 = pregunta(QuestionType.SHORT, "5");

        BigDecimal nota = Puntuacio.notaSobreDeu(List.of(seccio, p1, p2), Map.of(p1.getId(), resposta(null, "12")));

        assertThat(Puntuacio.maxim(List.of(seccio, p1, p2))).isEqualByComparingTo("20");
        assertThat(nota).isEqualByComparingTo("6");
    }

    @Test
    void nota_sobre_10_arrodoneix_a_dos_decimals_i_sense_punts_es_null() {
        assertThat(Puntuacio.sobreDeu(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("3.33");
        assertThat(Puntuacio.sobreDeu(new BigDecimal("7"), new BigDecimal("14"))).isEqualByComparingTo("5");
        assertThat(Puntuacio.sobreDeu(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }
}
