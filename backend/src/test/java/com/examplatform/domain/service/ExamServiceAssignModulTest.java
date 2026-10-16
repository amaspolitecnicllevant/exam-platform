package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.port.ExamParser;
import com.examplatform.dto.ExamDto;
import com.examplatform.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamServiceAssignModulTest {

    @Mock ExamRepository          examRepository;
    @Mock ExamParser              examParser;
    @Mock GrupRepository          grupRepository;
    @Mock QuestionRepository      questionRepository;
    @Mock ModulRepository         modulRepository;
    @Mock ImparticioRepository    imparticioRepository;
    @Mock AulaRepository          aulaRepository;
    @Mock ExamSessionRepository   sessionRepository;
    @Mock QuestionFileRepository  questionFileRepository;
    @Mock com.examplatform.infrastructure.persistence.AnswerRepository answerRepository;
    @Mock com.examplatform.infrastructure.storage.FitxersRespostaStorage fitxersStorage;
    @Mock AudienciaExamenService audiencia;

    ExamService service;

    User professor;
    User admin;
    User altreProf;

    @BeforeEach
    void setUp() {
        service   = new ExamService(examRepository, examParser, grupRepository,
                questionRepository, modulRepository, imparticioRepository, aulaRepository,
                sessionRepository, questionFileRepository, answerRepository, fitxersStorage, audiencia,
                org.mockito.Mockito.mock(ImportadorImatgesService.class));
        professor = user(Role.PROFESSOR);
        admin     = user(Role.ADMIN);
        altreProf = user(Role.PROFESSOR);
    }

    // ── assignModul ───────────────────────────────────────────────────────────

    @Test
    void assignModul_professor_amb_imparticio_assigna_correctament() {
        Exam exam  = exam(professor);
        Modul modul = modul();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId()))
                .thenReturn(true);
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto dto = service.assignModul(exam.getId(), modul.getId(), professor);

        assertThat(dto.modulId()).isEqualTo(modul.getId());
        assertThat(dto.modulNom()).isEqualTo(modul.getNom());
    }

    @Test
    void assignModul_professor_sense_imparticio_llanca_excepcio() {
        Exam exam  = exam(professor);
        Modul modul = modul();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.assignModul(exam.getId(), modul.getId(), professor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(modul.getCodi());
    }

    @Test
    void assignModul_admin_bypassa_verificacio_imparticio() {
        Exam exam  = exam(altreProf);
        Modul modul = modul();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // admin bypassa tant ownership com impartició
        assertThatNoException().isThrownBy(
                () -> service.assignModul(exam.getId(), modul.getId(), admin));

        verifyNoInteractions(imparticioRepository);
    }

    @Test
    void assignModul_senseOwnership_llanca_excepcio() {
        Exam exam = exam(altreProf);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.assignModul(exam.getId(), UUID.randomUUID(), professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignModul_modul_inexistent_llanca_excepcio() {
        Exam exam = exam(professor);
        UUID modulId = UUID.randomUUID();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modulId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignModul(exam.getId(), modulId, professor))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).role(role)
                .email(UUID.randomUUID() + "@test.cat").name("Test").build();
    }

    @Test
    void assignModul_no_rebutja_preguntes_amb_una_competencia_que_el_modul_no_te() {
        Exam exam = exam(professor);
        exam.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1)
                .tipus(QuestionType.SHORT).competencia("CO9").build());
        Modul modul = modul();
        modul.setCompetencies(new java.util.ArrayList<>(java.util.List.of(
                Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO1").build())));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);

        when(examRepository.save(exam)).thenReturn(exam);

        ExamDto dto = service.assignModul(exam.getId(), modul.getId(), professor);

        assertThat(dto.avisos()).singleElement().asString().contains("CO9").contains("no són a la programació");
        verify(examRepository).save(exam);
    }

    @Test
    void assignModul_accepta_preguntes_amb_competencies_del_modul() {
        Exam exam = exam(professor);
        exam.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1)
                .tipus(QuestionType.SHORT).competencia("CO1").build());
        Modul modul = modul();
        modul.setCompetencies(new java.util.ArrayList<>(java.util.List.of(
                Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO1").build())));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> service.assignModul(exam.getId(), modul.getId(), professor));
    }

    @Test
    void assignModul_d_un_altre_modul_treu_les_unitats_del_modul_anterior() {
        Modul anterior = modul();
        Exam exam = exam(professor);
        exam.setModul(anterior);
        exam.getUnitats().add(UnitatTreball.builder().id(UUID.randomUUID()).modul(anterior).codi("UT01").build());
        Modul nou = modul();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(nou.getId())).thenReturn(Optional.of(nou));
        when(imparticioRepository.professorImparteixModul(professor.getId(), nou.getId())).thenReturn(true);
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto dto = service.assignModul(exam.getId(), nou.getId(), professor);

        assertThat(dto.unitats()).isEmpty();
    }

    @Test
    void assignModul_al_mateix_modul_conserva_les_unitats() {
        Modul modul = modul();
        Exam exam = exam(professor);
        exam.setModul(modul);
        exam.getUnitats().add(UnitatTreball.builder().id(UUID.randomUUID()).modul(modul).codi("UT01").build());
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto dto = service.assignModul(exam.getId(), modul.getId(), professor);

        assertThat(dto.unitats()).extracting(u -> u.codi()).containsExactly("UT01");
    }

    private Exam exam(User owner) {
        return Exam.builder().id(UUID.randomUUID()).title("Examen test")
                .durada(60).status(ExamStatus.PUBLISHED).createdBy(owner)
                .penalitzacioChoice(BigDecimal.ZERO).build();
    }

    private Modul modul() {
        Departament dep  = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        Cicle cicle      = Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX").departament(dep).build();
        return Modul.builder().id(UUID.randomUUID()).codi("0483")
                .nom("Sistemes Informàtics").cicle(cicle).build();
    }
}
