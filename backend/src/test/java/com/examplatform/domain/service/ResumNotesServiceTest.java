package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ResumNotesDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ResumNotesServiceTest {

    @Mock ExamRepository examRepository;
    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository answerRepository;

    ResumNotesService service;
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    final List<Answer> respostes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ResumNotesService(examRepository, sessionRepository, answerRepository);
        lenient().when(answerRepository.findBySessionIdIn(any())).thenReturn(respostes);
    }

    /** Examen d'una sola pregunta de 10 punts; cada nota és la de la pregunta d'un alumne entregat (null = no entregat). */
    private Exam examen(String titol, String ut, String... notes) {
        Exam exam = Exam.builder().id(UUID.randomUUID()).title(titol).ut(ut).status(ExamStatus.CLOSED)
                .createdAt(LocalDateTime.now()).questions(new ArrayList<>()).build();
        Question q = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1).tipus(QuestionType.SHORT)
                .punts(BigDecimal.TEN).enunciat("p").build();
        exam.getQuestions().add(q);
        List<ExamSession> sessions = new ArrayList<>();
        for (String nota : notes) {
            ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam)
                    .status(nota == null ? SessionStatus.IN_PROGRESS : SessionStatus.SUBMITTED).build();
            sessions.add(s);
            if (nota != null) respostes.add(Answer.builder().session(s).question(q).contingut("x").manualScore(new BigDecimal(nota)).build());
        }
        lenient().when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(sessions);
        return exam;
    }

    @Test
    void aprovats_i_mitjana_de_cada_examen_nomes_amb_els_entregats() {
        Exam e = examen("UT01 A", "UT01", "8", "4", "5", null);
        org.mockito.Mockito.when(examRepository.findGestionablesPer(professor.getId())).thenReturn(List.of(e));

        ResumNotesDto r = service.resum(professor);

        ResumNotesDto.Examen x = r.examens().get(0);
        assertThat(x.entregats()).isEqualTo(3);                     // el no entregat no compta
        assertThat(x.aprovats()).isEqualTo(2);                      // 8 i 5 (el 5 aprova)
        assertThat(x.percentAprovats()).isEqualByComparingTo("66.7");
        assertThat(x.mitjana()).isEqualByComparingTo("5.67");       // (8 + 4 + 5) / 3
    }

    @Test
    void la_mitjana_de_la_ut_ajunta_les_notes_de_tots_els_seus_examens() {
        Exam a = examen("UT01 Model A", "UT01", "10", "0");
        Exam b = examen("UT01 Recuperació", "UT01", "6");
        Exam c = examen("UT02", "UT02", "3");
        Exam sense = examen("Prova", null, "9");
        org.mockito.Mockito.when(examRepository.findGestionablesPer(professor.getId())).thenReturn(List.of(a, b, c, sense));

        ResumNotesDto r = service.resum(professor);

        assertThat(r.unitats()).extracting(ResumNotesDto.Unitat::ut).containsExactly("UT01", "UT02");   // sense UT no hi surt
        ResumNotesDto.Unitat ut01 = r.unitats().get(0);
        assertThat(ut01.examens()).isEqualTo(2);
        assertThat(ut01.entregats()).isEqualTo(3);
        assertThat(ut01.aprovats()).isEqualTo(2);
        assertThat(ut01.mitjana()).isEqualByComparingTo("5.33");    // (10 + 0 + 6) / 3
        assertThat(r.unitats().get(1).mitjana()).isEqualByComparingTo("3");
        assertThat(r.examens()).hasSize(4);                         // però l'examen sense UT sí que surt al llistat
    }

    @Test
    void un_examen_sense_entregats_no_te_percentatge_ni_mitjana() {
        Exam e = examen("UT03", "UT03", (String) null);
        org.mockito.Mockito.when(examRepository.findGestionablesPer(professor.getId())).thenReturn(List.of(e));

        ResumNotesDto.Examen x = service.resum(professor).examens().get(0);

        assertThat(x.entregats()).isZero();
        assertThat(x.percentAprovats()).isNull();
        assertThat(x.mitjana()).isNull();
    }
}
