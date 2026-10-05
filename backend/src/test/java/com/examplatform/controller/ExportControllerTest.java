package com.examplatform.controller;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.ExamService;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository answerRepository;
    @Mock ExamService examService;

    @Test
    void la_fila_total_porta_el_maxim_real_i_el_nom_es_neteja_de_formules() throws Exception {
        User prof = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        Exam exam = Exam.builder().id(UUID.randomUUID()).title("Parcial").build();
        exam.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1)
                .tipus(QuestionType.TEXT).punts(new BigDecimal("12")).build());
        exam.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(exam).ordre(2)
                .tipus(QuestionType.TEXT).punts(new BigDecimal("8")).build());
        User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT)
                .name("=HYPERLINK(\"http://mal\")").email("a@x.cat").build();
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(alumne).build();
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(List.of(s));
        when(answerRepository.findBySessionId(s.getId())).thenReturn(List.of());

        byte[] csv = new ExportController(sessionRepository, answerRepository, examService)
                .exportCsv(exam.getId(), prof).getBody();
        String text = new String(csv, StandardCharsets.UTF_8);

        String total = text.lines().filter(l -> l.contains("TOTAL")).findFirst().orElseThrow();
        assertThat(total).contains(",TOTAL,,20,");
        assertThat(text).doesNotContain("\n=HYPERLINK").contains("'=HYPERLINK");
    }

    @Test
    void neteja_totes_les_formules_d_excel() {
        for (String f : List.of("=1+1", "+1", "-1", "@SUM(A1)")) {
            assertThat(ExportController.sanitizeCsvCell(f)).startsWith("'");
        }
        assertThat(ExportController.sanitizeCsvCell("Anna")).isEqualTo("Anna");
    }
}
