package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.ExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Pes (%) per part: l'escalat dels punts (Puntuacio) i la seva validació (PesosPartsService). */
class PesosPartsTest {

    Exam exam;
    Question s1, s2, s3, a1, a2, b1, c1;
    final List<Question> totes = new ArrayList<>();
    PesosPartsService service;

    private Question q(int ordre, QuestionType t, String punts, String titol) {
        Question q = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(ordre).tipus(t)
                .punts(new BigDecimal(punts)).enunciat(titol).build();
        totes.add(q);
        return q;
    }

    private Answer resposta(Question q, String nota) {
        return Answer.builder().question(q).manualScore(new BigDecimal(nota)).build();
    }

    /** Part 1: 2 preguntes de 2 (4 pts), Part 2: 1 de 4, Part 3: 1 de 2 -> total 10 pts. */
    @BeforeEach
    void setUp() {
        exam = Exam.builder().id(UUID.randomUUID()).questions(totes).build();
        s1 = q(1, QuestionType.SECTION, "0", "Part 1");
        a1 = q(2, QuestionType.SHORT, "2", "a1");
        a2 = q(3, QuestionType.SHORT, "2", "a2");
        s2 = q(4, QuestionType.SECTION, "0", "Part 2");
        b1 = q(5, QuestionType.SHORT, "4", "b1");
        s3 = q(6, QuestionType.SECTION, "0", "Part 3");
        c1 = q(7, QuestionType.SHORT, "2", "c1");
        service = new PesosPartsService(mock(ExamRepository.class));
    }

    private void pesos(String p1, String p2, String p3) {
        s1.setPesPart(new BigDecimal(p1));
        s2.setPesPart(new BigDecimal(p2));
        s3.setPesPart(new BigDecimal(p3));
    }

    @Test
    void sense_pesos_els_punts_no_canvien() {
        assertThat(Puntuacio.factor(a1)).isEqualByComparingTo("1");
        assertThat(Puntuacio.maxim(List.of(a1, a2, b1, c1))).isEqualByComparingTo("10");
        assertThat(Puntuacio.punts(a1, resposta(a1, "1.5"))).isEqualByComparingTo("1.5");
    }

    @Test
    void amb_pesos_cada_part_val_el_seu_percentatge_del_total() {
        pesos("40", "40", "20");    // abans: 4 / 4 / 2 punts de 10 (40 / 40 / 20): sense canvi
        assertThat(Puntuacio.maxim(List.of(a1, a2))).isEqualByComparingTo("4");

        pesos("50", "40", "10");    // la part 3 (difícil) passa a valer 1 punt de 10, la 1 en passa a valer 5
        assertThat(Puntuacio.maxim(List.of(a1, a2))).isEqualByComparingTo("5");
        assertThat(Puntuacio.maxim(List.of(b1))).isEqualByComparingTo("4");
        assertThat(Puntuacio.maxim(List.of(c1))).isEqualByComparingTo("1");
        assertThat(Puntuacio.maxim(List.of(a1, a2, b1, c1))).isEqualByComparingTo("10");   // el total no canvia
    }

    @Test
    void la_nota_de_l_alumne_es_reescala_amb_el_pes_de_la_part() {
        pesos("50", "40", "10");
        // Treu la meitat de la part 3 (1 de 2) -> 0,5 sobre el seu 1 de pes; tot a la 1 i la 2 a zero
        Map<UUID, Answer> r = new HashMap<>();
        r.put(a1.getId(), resposta(a1, "2"));
        r.put(a2.getId(), resposta(a2, "2"));
        r.put(b1.getId(), resposta(b1, "0"));
        r.put(c1.getId(), resposta(c1, "1"));

        assertThat(Puntuacio.total(List.of(a1, a2, b1, c1), r)).isEqualByComparingTo("5.5");
        assertThat(Puntuacio.notaSobreDeu(List.of(a1, a2, b1, c1), r)).isEqualByComparingTo("5.50");
    }

    @Test
    void una_pregunta_anulada_dona_els_punts_escalats_a_tothom() {
        pesos("50", "40", "10");
        c1.setAnulada(true);
        assertThat(Puntuacio.punts(c1, null)).isEqualByComparingTo("1");
    }

    @Test
    void valida_que_els_pesos_sumin_100() {
        assertThatThrownBy(() -> service.fixa(exam, List.of(
                new PesosPartsService.PesPart(s1.getId(), new BigDecimal("50")),
                new PesosPartsService.PesPart(s2.getId(), new BigDecimal("40")),
                new PesosPartsService.PesPart(s3.getId(), new BigDecimal("20")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sumar 100");
        assertThat(s1.getPesPart()).isNull();     // no s'ha desat res
    }

    @Test
    void exigeix_el_pes_de_totes_les_parts_i_que_siguin_de_l_examen() {
        assertThatThrownBy(() -> service.fixa(exam, List.of(
                new PesosPartsService.PesPart(s1.getId(), new BigDecimal("60")),
                new PesosPartsService.PesPart(s2.getId(), new BigDecimal("40")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Part 3");
        assertThatThrownBy(() -> service.fixa(exam, List.of(
                new PesosPartsService.PesPart(s1.getId(), new BigDecimal("50")),
                new PesosPartsService.PesPart(s2.getId(), new BigDecimal("30")),
                new PesosPartsService.PesPart(s3.getId(), new BigDecimal("20")),
                new PesosPartsService.PesPart(UUID.randomUUID(), BigDecimal.ZERO))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no són d'aquest examen");
    }

    @Test
    void desa_els_pesos_vàlids_i_es_poden_treure() {
        String resum = service.fixa(exam, List.of(
                new PesosPartsService.PesPart(s1.getId(), new BigDecimal("50")),
                new PesosPartsService.PesPart(s2.getId(), new BigDecimal("40")),
                new PesosPartsService.PesPart(s3.getId(), new BigDecimal("10"))));
        assertThat(resum).isEqualTo("Part 1=50%; Part 2=40%; Part 3=10%");
        assertThat(Puntuacio.maxim(List.of(c1))).isEqualByComparingTo("1");

        service.treu(exam);
        assertThat(Puntuacio.maxim(List.of(c1))).isEqualByComparingTo("2");
    }

    @Test
    void no_deixa_repartir_pesos_si_hi_ha_preguntes_abans_de_la_primera_part() {
        Question solta = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(0).tipus(QuestionType.SHORT)
                .punts(BigDecimal.ONE).enunciat("solta").build();
        totes.add(solta);
        assertThatThrownBy(() -> service.fixa(exam, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("abans de la primera part");
    }

    @Test
    void sense_seccions_no_es_poden_posar_pesos() {
        totes.removeIf(q -> q.getTipus() == QuestionType.SECTION);
        assertThatThrownBy(() -> service.fixa(exam, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no té parts");
    }
}
