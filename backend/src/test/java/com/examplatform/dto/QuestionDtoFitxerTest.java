package com.examplatform.dto;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.FormatsFitxer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDtoFitxerTest {

    private static Question pregunta(QuestionType tipus, String formats) {
        return Question.builder().id(UUID.randomUUID()).ordre(1).tipus(tipus).enunciat("x")
                .punts(new BigDecimal("2")).formatsPermesos(formats).build();
    }

    @Test
    void l_alumne_veu_els_formats_de_la_pregunta_de_fitxer() {
        assertThat(QuestionDto.forStudent(pregunta(QuestionType.FILE_UPLOAD, "docx,pkt")).formatsPermesos())
                .containsExactly("docx", "pkt");
    }

    @Test
    void sense_llista_a_la_pregunta_l_alumne_veu_tots_els_formats_permesos() {
        assertThat(QuestionDto.forStudent(pregunta(QuestionType.FILE_UPLOAD, null)).formatsPermesos())
                .isEqualTo(FormatsFitxer.permesos());
    }

    @Test
    void les_preguntes_d_altres_tipus_no_tenen_formats() {
        assertThat(QuestionDto.forStudent(pregunta(QuestionType.SHORT, null)).formatsPermesos()).isNull();
        assertThat(QuestionDto.from(pregunta(QuestionType.CHOICE, null)).formatsPermesos()).isNull();
    }

    @Test
    void el_professor_tambe_els_veu() {
        assertThat(QuestionDto.from(pregunta(QuestionType.FILE_UPLOAD, "pdf")).formatsPermesos()).isEqualTo(List.of("pdf"));
    }
}
