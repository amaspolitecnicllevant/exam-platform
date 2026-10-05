package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ExamStatsDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock ExamService examService;
    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository answerRepository;

    StatsService service;
    Exam exam;
    Question test;     // CHOICE, 4 punts, correcta b
    Question curta;    // SHORT, 6 punts
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    final List<Answer> respostes = new ArrayList<>();
    final List<ExamSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StatsService(examService, sessionRepository, answerRepository);
        exam = Exam.builder().id(UUID.randomUUID()).questions(new ArrayList<>()).build();
        exam.getQuestions().add(Question.builder().id(UUID.randomUUID()).ordre(1).tipus(QuestionType.SECTION)
                .punts(BigDecimal.ZERO).enunciat("Part 1").build());
        test = Question.builder().id(UUID.randomUUID()).ordre(2).tipus(QuestionType.CHOICE).punts(new BigDecimal("4"))
                .enunciat("Quin port?").choices("a) 53\nb) 67\nc) 80\nd) 443").correctChoice("b").build();
        curta = Question.builder().id(UUID.randomUUID()).ordre(3).tipus(QuestionType.SHORT).punts(new BigDecimal("6"))
                .enunciat("Què fa DHCP?").build();
        exam.getQuestions().add(test);
        exam.getQuestions().add(curta);
        lenient().when(examService.getEntity(exam.getId())).thenReturn(exam);
        lenient().when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(sessions);
        lenient().when(answerRepository.findBySessionIdIn(any())).thenReturn(respostes);
    }

    /** Alumne entregat amb resposta de test (lletra) i nota de la curta (null = sense resposta). */
    private void alumne(String lletra, String notaCurta) {
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam)
                .status(SessionStatus.SUBMITTED).build();
        sessions.add(s);
        if (lletra != null) {
            respostes.add(Answer.builder().session(s).question(test).contingut(lletra)
                    .autoScore(lletra.equals("b") ? new BigDecimal("4") : BigDecimal.ZERO).build());
        }
        if (notaCurta != null) {
            respostes.add(Answer.builder().session(s).question(curta).contingut("resposta")
                    .manualScore(new BigDecimal(notaCurta)).build());
        }
    }

    @Test
    void resum_de_notes_i_aprovats() {
        alumne("b", "6");    // 10
        alumne("b", "3");    // 7
        alumne("a", "4");    // 4
        alumne("c", null);   // 0

        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.entregats()).isEqualTo(4);
        assertThat(st.mitjana()).isEqualByComparingTo("5.25");
        assertThat(st.mediana()).isEqualByComparingTo("5.5");
        assertThat(st.minima()).isEqualByComparingTo("0");
        assertThat(st.maxima()).isEqualByComparingTo("10");
        assertThat(st.percentAprovats()).isEqualByComparingTo("50.0");
        assertThat(st.histograma()).containsExactly(1, 0, 0, 0, 1, 0, 0, 1, 0, 1);
    }

    @Test
    void les_notes_es_passen_a_base_10_si_l_examen_no_val_10_punts() {
        test.setPunts(new BigDecimal("8"));    // examen sobre 20: 8 + 12
        curta.setPunts(new BigDecimal("12"));
        alumne("b", "12");   // el test encertat puntua 4 (al mock): 4 + 12 = 16/20 → 8
        alumne("a", "5");    // 0 + 5 = 5/20 → 2,5

        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.maxima()).isEqualByComparingTo("8");
        assertThat(st.minima()).isEqualByComparingTo("2.5");
        assertThat(st.percentAprovats()).isEqualByComparingTo("50.0");
        assertThat(st.histograma()).containsExactly(0, 0, 1, 0, 0, 0, 0, 0, 1, 0);
        // Les estadístiques per pregunta continuen en punts
        assertThat(st.preguntes().get(1).punts()).isEqualByComparingTo("12");
    }

    @Test
    void estadistiques_de_la_pregunta_de_test() {
        alumne("b", "6");
        alumne("b", "3");
        alumne("a", "4");
        alumne(null, null);   // no l'ha contestada

        ExamStatsDto.Pregunta p = service.stats(exam.getId(), professor).preguntes().get(0);

        assertThat(p.ordre()).isEqualTo(2);
        assertThat(p.respostes()).isEqualTo(3);
        assertThat(p.senseResposta()).isEqualTo(1);
        assertThat(p.percentCorrectes()).isEqualByComparingTo("50.0");
        assertThat(p.correcta()).isEqualTo("b");
        assertThat(p.opcions()).containsExactly(
                java.util.Map.entry("a", 1), java.util.Map.entry("b", 2),
                java.util.Map.entry("c", 0), java.util.Map.entry("d", 0));
        assertThat(p.mitjanaPunts()).isEqualByComparingTo("2");      // (4+4+0+0)/4
        assertThat(p.percentRendiment()).isEqualByComparingTo("50.0");
    }

    @Test
    void la_pregunta_de_text_no_te_opcions_ni_percent_correctes() {
        alumne("b", "3");
        ExamStatsDto.Pregunta p = service.stats(exam.getId(), professor).preguntes().get(1);

        assertThat(p.opcions()).isNull();
        assertThat(p.percentCorrectes()).isNull();
        assertThat(p.percentRendiment()).isEqualByComparingTo("50.0");
    }

    @Test
    void el_bonus_compta_els_punts_sencers_per_a_tothom() {
        test.setAnulada(true);
        alumne("a", "6");
        alumne(null, "2");

        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.mitjana()).isEqualByComparingTo("8");   // (10 + 6) / 2
        assertThat(st.preguntes().get(0).bonus()).isTrue();
        assertThat(st.preguntes().get(0).percentRendiment()).isEqualByComparingTo("100.0");
    }

    @Test
    void respostes_sense_nota_es_compten_com_a_pendents() {
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).status(SessionStatus.SUBMITTED).build();
        sessions.add(s);
        respostes.add(Answer.builder().session(s).question(curta).contingut("sense corregir").build());

        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.respostesPendents()).isEqualTo(1);
        assertThat(st.mitjana()).isEqualByComparingTo("0");
    }

    @Test
    void les_sessions_no_entregades_no_compten() {
        alumne("b", "6");
        sessions.add(ExamSession.builder().id(UUID.randomUUID()).exam(exam).status(SessionStatus.IN_PROGRESS).build());

        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.sessions()).isEqualTo(2);
        assertThat(st.entregats()).isEqualTo(1);
        assertThat(st.mitjana()).isEqualByComparingTo("10");
    }

    @Test
    void sense_entregues_no_hi_ha_dades() {
        ExamStatsDto st = service.stats(exam.getId(), professor);

        assertThat(st.entregats()).isZero();
        assertThat(st.mitjana()).isNull();
        assertThat(st.mediana()).isNull();
        assertThat(st.percentAprovats()).isNull();
        assertThat(st.histograma()).containsOnly(0).hasSize(10);
        assertThat(st.preguntes()).hasSize(2);
        verifyNoInteractions(answerRepository);
    }

    @Test
    void professor_sense_permis_no_veu_estadistiques() {
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(exam, professor);

        assertThatThrownBy(() -> service.stats(exam.getId(), professor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessionRepository, answerRepository);
    }

    @Test
    void histograma_posa_el_10_a_la_darrera_franja_i_les_negatives_a_la_primera() {
        assertThat(StatsService.histograma(List.of(new BigDecimal("10"), new BigDecimal("9.99"),
                new BigDecimal("-0.5"), new BigDecimal("4.99"), new BigDecimal("5"))))
                .containsExactly(1, 0, 0, 0, 1, 1, 0, 0, 0, 2);
    }

    @Test
    void mediana_amb_nombre_senar_i_parell() {
        assertThat(StatsService.mediana(List.of(new BigDecimal("3"), new BigDecimal("9"), new BigDecimal("5"))))
                .isEqualByComparingTo("5");
        assertThat(StatsService.mediana(List.of(new BigDecimal("3"), new BigDecimal("9"))))
                .isEqualByComparingTo("6");
    }
}
