package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.AlumneAccesDto;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.MatriculaRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AudienciaExamenServiceTest {

    ExamSessionRepository sessionRepository = mock(ExamSessionRepository.class);
    MatriculaRepository matriculaRepository = mock(MatriculaRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AudienciaExamenService service;

    Modul modul = Modul.builder().id(UUID.randomUUID()).nom("Sistemes").build();
    Exam exam = Exam.builder().id(UUID.randomUUID()).title("Ex").restringit(true).modul(modul).build();
    User anna = alumne("Anna"), bernat = alumne("Bernat");

    private static User alumne(String nom) {
        return User.builder().id(UUID.randomUUID()).name(nom).email(nom.toLowerCase() + "@x.cat").role(Role.STUDENT).build();
    }

    @BeforeEach
    void setUp() {
        service = new AudienciaExamenService(sessionRepository, matriculaRepository, userRepository);
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<User> out = new java.util.ArrayList<>();
            for (UUID id : ids) for (User u : List.of(anna, bernat)) if (u.getId().equals(id)) out.add(u);
            return out;
        });
        when(matriculaRepository.findAlumneIdsByModulId(modul.getId())).thenReturn(List.of(anna.getId(), bernat.getId()));
        when(sessionRepository.findStudentIdsByExamId(exam.getId())).thenReturn(List.of());
    }

    @Test
    void assigna_crea_la_sessio_pendent_dels_alumnes_triats() {
        int n = service.assigna(exam, List.of(anna.getId(), bernat.getId()));

        assertThat(n).isEqualTo(2);
        var c = org.mockito.ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionRepository, times(2)).save(c.capture());
        assertThat(c.getAllValues()).allSatisfy(s -> {
            assertThat(s.getExam()).isSameAs(exam);
            assertThat(s.getStartedAt()).isNull();   // el rellotge comença quan l'alumne obre l'examen
        });
    }

    @Test
    void assigna_no_duplica_els_que_ja_tenen_sessio() {
        when(sessionRepository.findStudentIdsByExamId(exam.getId())).thenReturn(List.of(anna.getId()));

        assertThat(service.assigna(exam, List.of(anna.getId(), bernat.getId()))).isEqualTo(1);
        verify(sessionRepository, times(1)).save(any());
    }

    @Test
    void assigna_rebutja_llista_buida_i_no_alumnes_sense_crear_res() {
        User prof = User.builder().id(UUID.randomUUID()).name("Prof").role(Role.PROFESSOR).build();
        doReturn(List.of(prof)).when(userRepository).findAllById(any());

        assertThatThrownBy(() -> service.assigna(exam, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assigna(exam, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assigna(exam, List.of(prof.getId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alumnes");
        assertThatThrownBy(() -> service.assigna(exam, List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void assigna_rebutja_alumnes_no_matriculats_al_modul_i_els_nomena() {
        when(matriculaRepository.findAlumneIdsByModulId(modul.getId())).thenReturn(List.of(anna.getId()));

        assertThatThrownBy(() -> service.assigna(exam, List.of(anna.getId(), bernat.getId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Bernat")
                .hasMessageNotContaining("Anna");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void treu_esborra_la_sessio_si_l_alumne_encara_no_ha_comencat() {
        ExamSession pendent = ExamSession.builder().exam(exam).student(anna).build();
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), anna.getId())).thenReturn(Optional.of(pendent));

        service.treu(exam, anna.getId());

        verify(sessionRepository).delete(pendent);
    }

    @Test
    void treu_no_pot_treure_qui_ja_ha_comencat_o_entregat() {
        ExamSession enCurs = ExamSession.builder().exam(exam).student(anna).startedAt(LocalDateTime.now()).build();
        ExamSession entregada = ExamSession.builder().exam(exam).student(bernat).startedAt(LocalDateTime.now())
                .status(SessionStatus.SUBMITTED).build();
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), anna.getId())).thenReturn(Optional.of(enCurs));
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), bernat.getId())).thenReturn(Optional.of(entregada));

        assertThatThrownBy(() -> service.treu(exam, anna.getId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.treu(exam, bernat.getId())).isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void treu_alumne_sense_acces_no_es_troba() {
        when(sessionRepository.findByExamIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.treu(exam, anna.getId())).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void llista_indica_l_estat_de_cada_alumne_ordenats_per_nom() {
        User carla = alumne("Carla");
        when(sessionRepository.findByExamIdWithDetails(exam.getId())).thenReturn(List.of(
                ExamSession.builder().exam(exam).student(carla).status(SessionStatus.SUBMITTED).startedAt(LocalDateTime.now()).build(),
                ExamSession.builder().exam(exam).student(anna).build(),
                ExamSession.builder().exam(exam).student(bernat).startedAt(LocalDateTime.now()).build()));

        var l = service.llista(exam);

        assertThat(l.restringit()).isTrue();
        assertThat(l.alumnes()).extracting(AlumneAccesDto::nom).containsExactly("Anna", "Bernat", "Carla");
        assertThat(l.alumnes()).extracting(AlumneAccesDto::estat).containsExactly(
                AlumneAccesDto.Estat.PENDENT, AlumneAccesDto.Estat.EN_CURS, AlumneAccesDto.Estat.ENTREGAT);
    }
}
