package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.CopiesInformeDto;
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
class CopiesServiceTest {

    @Mock ExamService examService;
    @Mock ExamSessionRepository sessionRepository;
    @Mock AnswerRepository answerRepository;
    @Mock ConfiguracioService configuracioService;

    CopiesService service;
    Exam exam;
    Question llarga;
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    final List<ExamSession> sessions = new ArrayList<>();
    final List<Answer> respostes = new ArrayList<>();
    final ConfiguracioSistema config = new ConfiguracioSistema();

    static final String TEXT = "El servidor DHCP s'encarrega de donar a cada ordinador de la xarxa una adreça IP "
            + "lliure i també li indica la porta d'enllaç i els servidors DNS que ha de fer servir";

    @BeforeEach
    void setUp() {
        service = new CopiesService(examService, sessionRepository, answerRepository, configuracioService);
        exam = Exam.builder().id(UUID.randomUUID()).questions(new ArrayList<>()).build();
        llarga = Question.builder().id(UUID.randomUUID()).ordre(1).tipus(QuestionType.LONG)
                .punts(BigDecimal.TEN).enunciat("Explica el DHCP.").build();
        exam.getQuestions().add(llarga);
        lenient().when(examService.getEntity(exam.getId())).thenReturn(exam);
        lenient().when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(sessions);
        lenient().when(answerRepository.findBySessionIdIn(any())).thenReturn(respostes);
        lenient().when(configuracioService.get()).thenReturn(config);
    }

    private ExamSession alumne(String nom, SessionStatus estat, String resposta) {
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).status(estat)
                .student(User.builder().id(UUID.randomUUID()).name(nom).build()).build();
        sessions.add(s);
        if (estat == SessionStatus.SUBMITTED) {
            respostes.add(Answer.builder().session(s).question(llarga).contingut(resposta).build());
        }
        return s;
    }

    @Test
    void informe_amb_els_noms_dels_alumnes_i_les_marques() {
        alumne("Anna", SessionStatus.SUBMITTED, TEXT);
        alumne("Biel", SessionStatus.SUBMITTED, TEXT);
        alumne("Carla", SessionStatus.IN_PROGRESS, null);

        CopiesInformeDto inf = service.informe(exam.getId(), professor);

        assertThat(inf.entregats()).isEqualTo(2);
        assertThat(inf.llindarPercent()).isEqualTo(80);
        assertThat(inf.parelles()).singleElement().satisfies(p -> {
            assertThat(List.of(p.alumneA(), p.alumneB())).containsExactlyInAnyOrder("Anna", "Biel");
            assertThat(p.coincidencies()).singleElement().satisfies(c -> {
                assertThat(c.enunciat()).isEqualTo("Explica el DHCP.");
                assertThat(c.marquesA()).isNotEmpty();
            });
        });
    }

    @Test
    void fa_servir_el_llindar_del_centre() {
        config.setCopiesLlindar(100);
        alumne("Anna", SessionStatus.SUBMITTED, TEXT);
        alumne("Biel", SessionStatus.SUBMITTED, TEXT.replace("lliure", "disponible"));

        assertThat(service.informe(exam.getId(), professor).parelles()).isEmpty();
    }

    @Test
    void sense_entregues_l_informe_es_buit() {
        alumne("Carla", SessionStatus.IN_PROGRESS, null);

        CopiesInformeDto inf = service.informe(exam.getId(), professor);

        assertThat(inf.parelles()).isEmpty();
        verifyNoInteractions(answerRepository);
    }

    @Test
    void professor_sense_permis_no_veu_l_informe() {
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(exam, professor);

        assertThatThrownBy(() -> service.informe(exam.getId(), professor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessionRepository, answerRepository);
    }
}
