package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NumeracioTest {

    private static Question q(Exam exam, int ordre, QuestionType tipus) {
        Question q = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(ordre).tipus(tipus).build();
        exam.getQuestions().add(q);
        return q;
    }

    private static Exam examen() {
        Exam e = new Exam();
        e.setQuestions(new ArrayList<>());
        return e;
    }

    @Test
    void les_seccions_no_compten_en_el_numero_visible() {
        Exam e = examen();
        Question s1 = q(e, 1, QuestionType.SECTION);
        Question p1 = q(e, 2, QuestionType.CHOICE);
        Question p2 = q(e, 3, QuestionType.SHORT);
        Question s2 = q(e, 4, QuestionType.SECTION);
        Question p3 = q(e, 5, QuestionType.BASH_CMD);

        Map<UUID, Integer> n = Numeracio.numeros(e.getQuestions());

        assertThat(n).doesNotContainKeys(s1.getId(), s2.getId());
        assertThat(n.get(p1.getId())).isEqualTo(1);
        assertThat(n.get(p2.getId())).isEqualTo(2);
        assertThat(n.get(p3.getId())).isEqualTo(3);
        assertThat(Numeracio.numero(p3)).isEqualTo(3);
        assertThat(p3.getOrdre()).isEqualTo(5);
    }

    @Test
    void sense_seccions_el_numero_es_l_ordre() {
        Exam e = examen();
        q(e, 1, QuestionType.CHOICE);
        Question ultima = q(e, 2, QuestionType.CHOICE);

        assertThat(Numeracio.numero(ultima)).isEqualTo(2);
    }

    @Test
    void s_ordena_per_ordre_encara_que_la_llista_vingui_desordenada() {
        Exam e = examen();
        Question b = q(e, 3, QuestionType.SHORT);
        Question sec = q(e, 1, QuestionType.SECTION);
        Question a = q(e, 2, QuestionType.CHOICE);

        assertThat(Numeracio.numero(a)).isEqualTo(1);
        assertThat(Numeracio.numero(b)).isEqualTo(2);
        assertThat(sec.getOrdre()).isEqualTo(1);
    }

    @Test
    void sense_examen_conegut_es_torna_l_ordre_intern() {
        Question orfa = Question.builder().id(UUID.randomUUID()).ordre(7).tipus(QuestionType.SHORT).build();

        assertThat(Numeracio.numero(orfa)).isEqualTo(7);
    }

    @Test
    void numeros_de_diverses_preguntes_son_correlatius_sense_forats() {
        Exam e = examen();
        List<Question> qs = new ArrayList<>();
        for (int i = 1; i <= 25; i++) qs.add(q(e, i, i % 9 == 1 ? QuestionType.SECTION : QuestionType.SHORT));

        Map<UUID, Integer> n = Numeracio.numeros(qs);

        long preguntes = qs.stream().filter(x -> x.getTipus() != QuestionType.SECTION).count();
        assertThat(n.values()).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, (int) preguntes).boxed().toList());
    }
}
