package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.GrupDto;
import com.examplatform.dto.RecuperacioDto;
import com.examplatform.dto.RecuperacioDto.Motiu;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.GrupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacioServiceTest {

    @Mock ExamService examService;
    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository answerRepository;
    @Mock GrupRepository grupRepository;

    RecuperacioService service;
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    Exam exam;
    Question q1, q2;
    final List<ExamSession> sessions = new ArrayList<>();
    final List<Answer> respostes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RecuperacioService(examService, sessionRepository, answerRepository, grupRepository);
        Modul modul = Modul.builder().id(UUID.randomUUID()).nom("Xarxes").build();
        exam = Exam.builder().id(UUID.randomUUID()).title("Parcial").modul(modul).build();
        // Examen sobre 20 punts: la nota es passa a base 10
        q1 = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1).tipus(QuestionType.TEXT)
                .punts(new BigDecimal("10")).build();
        q2 = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(2).tipus(QuestionType.TEXT)
                .punts(new BigDecimal("10")).build();
        Question seccio = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(0).tipus(QuestionType.SECTION)
                .punts(BigDecimal.ZERO).build();
        exam.getQuestions().addAll(List.of(seccio, q1, q2));

        lenient().when(examService.getEntity(exam.getId())).thenReturn(exam);
        lenient().when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(sessions);
        lenient().when(answerRepository.findBySessionIdIn(any())).thenReturn(respostes);
    }

    private User alumne(String nom, SessionStatus estat, String nota1, String nota2) {
        User u = User.builder().id(UUID.randomUUID()).name(nom).email(nom.toLowerCase() + "@x.cat").role(Role.STUDENT).build();
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(u).status(estat).build();
        sessions.add(s);
        if (nota1 != null || nota2 != null) {
            respostes.add(Answer.builder().session(s).question(q1).contingut("r")
                    .manualScore(nota1 == null ? null : new BigDecimal(nota1)).build());
            respostes.add(Answer.builder().session(s).question(q2).contingut("r")
                    .manualScore(nota2 == null ? null : new BigDecimal(nota2)).build());
        }
        return u;
    }

    @Test
    void llista_suspesos_i_no_presentats_pero_no_aprovats() {
        alumne("Aprovada", SessionStatus.SUBMITTED, "5", "5");     // 10/20 = 5
        alumne("Suspes", SessionStatus.SUBMITTED, "4", "5.8");     // 9.8/20 = 4.9
        alumne("Absent", SessionStatus.IN_PROGRESS, null, null);

        RecuperacioDto dto = service.candidats(exam.getId(), professor);

        assertThat(dto.candidats()).extracting(RecuperacioDto.Candidat::nom).containsExactly("Suspes", "Absent");
        RecuperacioDto.Candidat suspes = dto.candidats().get(0);
        assertThat(suspes.motiu()).isEqualTo(Motiu.SUSPES);
        assertThat(suspes.nota()).isEqualByComparingTo("4.9");
        assertThat(suspes.notaProvisional()).isFalse();
        assertThat(dto.candidats().get(1).motiu()).isEqualTo(Motiu.NO_PRESENTAT);
        assertThat(dto.candidats().get(1).nota()).isNull();
    }

    @Test
    void entregat_en_blanc_es_suspes_amb_un_zero() {
        alumne("Blanc", SessionStatus.SUBMITTED, null, null);

        RecuperacioDto dto = service.candidats(exam.getId(), professor);

        assertThat(dto.candidats()).singleElement()
                .satisfies(c -> {
                    assertThat(c.motiu()).isEqualTo(Motiu.SUSPES);
                    assertThat(c.nota()).isEqualByComparingTo("0");
                });
    }

    @Test
    void respostes_sense_nota_marquen_la_nota_com_a_provisional() {
        alumne("Pendent", SessionStatus.SUBMITTED, "3", null);

        RecuperacioDto dto = service.candidats(exam.getId(), professor);

        assertThat(dto.respostesPendents()).isEqualTo(1);
        assertThat(dto.candidats().get(0).notaProvisional()).isTrue();
        assertThat(dto.candidats().get(0).nota()).isEqualByComparingTo("1.5");
    }

    @Test
    void bonus_compta_per_a_tothom() {
        q2.setAnulada(true);
        alumne("AmbBonus", SessionStatus.SUBMITTED, "0", "0");   // 0 + 10 (bonus) = 10/20 = 5

        assertThat(service.candidats(exam.getId(), professor).candidats()).isEmpty();
    }

    @Test
    void sense_permis_no_llista() {
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(exam, professor);

        assertThatThrownBy(() -> service.candidats(exam.getId(), professor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crea_el_grup_del_modul_amb_els_alumnes_triats() {
        User a = alumne("A", SessionStatus.SUBMITTED, "1", "1");
        alumne("B", SessionStatus.SUBMITTED, "1", "1");
        when(grupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GrupDto dto = service.crearGrup(exam.getId(),
                new RecuperacioDto.CrearGrupRequest("  Recuperació Parcial ", List.of(a.getId())), professor);

        ArgumentCaptor<Grup> c = ArgumentCaptor.forClass(Grup.class);
        verify(grupRepository).save(c.capture());
        assertThat(c.getValue().getName()).isEqualTo("Recuperació Parcial");
        assertThat(c.getValue().getModul()).isSameAs(exam.getModul());
        assertThat(c.getValue().getCreatedBy()).isSameAs(professor);
        assertThat(c.getValue().getStudents()).containsExactly(a);
        assertThat(dto.students()).hasSize(1);
    }

    @Test
    void no_admet_alumnes_que_no_han_fet_l_examen() {
        alumne("A", SessionStatus.SUBMITTED, "1", "1");

        assertThatThrownBy(() -> service.crearGrup(exam.getId(),
                new RecuperacioDto.CrearGrupRequest("Rec", List.of(UUID.randomUUID())), professor))
                .isInstanceOf(IllegalArgumentException.class);
        verify(grupRepository, never()).save(any());
    }

    @Test
    void nom_buit_o_sense_alumnes_es_rebutja() {
        User a = alumne("A", SessionStatus.SUBMITTED, "1", "1");

        assertThatThrownBy(() -> service.crearGrup(exam.getId(),
                new RecuperacioDto.CrearGrupRequest("  ", List.of(a.getId())), professor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crearGrup(exam.getId(),
                new RecuperacioDto.CrearGrupRequest("Rec", List.of()), professor))
                .isInstanceOf(IllegalArgumentException.class);
        verify(grupRepository, never()).save(any());
    }

    @Test
    void sense_permis_no_crea_el_grup() {
        User a = alumne("A", SessionStatus.SUBMITTED, "1", "1");
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(exam, professor);

        assertThatThrownBy(() -> service.crearGrup(exam.getId(),
                new RecuperacioDto.CrearGrupRequest("Rec", List.of(a.getId())), professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(grupRepository, never()).save(any());
    }
}
