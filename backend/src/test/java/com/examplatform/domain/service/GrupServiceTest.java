package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrupServiceTest {

    @Mock GrupRepository grupRepository;
    @Mock UserRepository userRepository;
    @Mock ExamSessionRepository sessionRepository;
    @Mock ExamService examService;
    @Mock ModulRepository modulRepository;
    @Mock MatriculaRepository matriculaRepository;

    GrupService service;

    User professor;
    User admin;

    @BeforeEach
    void setUp() {
        service = new GrupService(grupRepository, userRepository, sessionRepository, examService, modulRepository, matriculaRepository);

        professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        admin     = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
    }

    // ── Creació ───────────────────────────────────────────────────────────────

    @Test
    void crea_grup_amb_el_professor_com_a_propietari() {
        when(grupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create("1r ASIX", professor);

        verify(grupRepository).save(argThat(g ->
                "1r ASIX".equals(g.getName()) && g.getCreatedBy() == professor));
    }

    // ── Assignació d'alumnes ──────────────────────────────────────────────────

    @Test
    void set_students_rebutja_usuaris_que_no_son_alumnes() {
        Grup grup = grup(professor);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        User noAlumne = User.builder().id(UUID.randomUUID())
                .email("prof@test.cat").role(Role.PROFESSOR).build();
        when(userRepository.findAllById(any())).thenReturn(List.of(noAlumne));

        assertThatThrownBy(() -> service.setStudents(grup.getId(), List.of(noAlumne.getId()), professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és un alumne");
    }

    @Test
    void set_students_professor_no_pot_modificar_grup_altrui() {
        User altreProf = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        Grup grup = grup(altreProf);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        assertThatThrownBy(() -> service.setStudents(grup.getId(), List.of(), professor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void admin_pot_modificar_qualsevol_grup() {
        Grup grup = grup(professor);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(grupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> service.setStudents(grup.getId(), List.of(), admin));
    }

    // ── Assignar examen ───────────────────────────────────────────────────────

    @Test
    void assignar_examen_crea_sessions_pels_alumnes_sense_sessio_previa() {
        User a1 = student(); User a2 = student(); User a3 = student();
        Grup grup = grupAmAlumnes(professor, a1, a2, a3);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        Exam exam = exam(ExamStatus.PUBLISHED);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        // a1 ja té sessió; a2 i a3 no
        when(sessionRepository.findStudentIdsByExamId(exam.getId()))
                .thenReturn(List.of(a1.getId()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.assignExam(grup.getId(), exam.getId(), professor);

        assertThat(created).isEqualTo(2);
        verify(sessionRepository, times(2)).save(any());
    }

    @Test
    void assignar_examen_d_un_altre_professor_llanca_AccessDenied_i_no_crea_sessions() {
        Grup grup = grupAmAlumnes(professor, student());
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));
        Exam exam = exam(ExamStatus.PUBLISHED);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                .when(examService).assertOwnership(exam, professor);

        assertThatThrownBy(() -> service.assignExam(grup.getId(), exam.getId(), professor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void assignar_examen_crea_sessions_sense_iniciar_el_rellotge() {
        Grup grup = grupAmAlumnes(professor, student());
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));
        Exam exam = exam(ExamStatus.PUBLISHED);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findStudentIdsByExamId(exam.getId())).thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assignExam(grup.getId(), exam.getId(), professor);

        verify(sessionRepository).save(argThat(sessio -> sessio.getStartedAt() == null));
    }

    @Test
    void assignar_examen_no_publicat_llanca_excepcio() {
        Grup grup = grup(professor);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        Exam exam = exam(ExamStatus.DRAFT);
        when(examService.getEntity(exam.getId())).thenReturn(exam);

        assertThatThrownBy(() -> service.assignExam(grup.getId(), exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicat");
    }

    @Test
    void assignar_examen_amb_modul_nomes_crea_sessions_per_alumnes_matriculats() {
        User a1 = student(); User a2 = student(); User a3 = student();
        Grup grup = grupAmAlumnes(professor, a1, a2, a3);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        Exam exam = examAmbModul(ExamStatus.PUBLISHED);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findStudentIdsByExamId(exam.getId())).thenReturn(List.of());
        // Només a1 i a2 estan matriculats al mòdul de l'examen
        when(matriculaRepository.findAlumneIdsByModulId(any()))
                .thenReturn(List.of(a1.getId(), a2.getId()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.assignExam(grup.getId(), exam.getId(), professor);

        assertThat(created).isEqualTo(2);
        verify(sessionRepository, times(2)).save(any());
    }

    @Test
    void assignar_examen_si_tots_ja_tenen_sessio_no_en_crea_de_noves() {
        User a1 = student();
        Grup grup = grupAmAlumnes(professor, a1);
        when(grupRepository.findById(grup.getId())).thenReturn(Optional.of(grup));

        Exam exam = exam(ExamStatus.PUBLISHED);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(sessionRepository.findStudentIdsByExamId(exam.getId()))
                .thenReturn(List.of(a1.getId()));

        int created = service.assignExam(grup.getId(), exam.getId(), professor);

        assertThat(created).isZero();
        verify(sessionRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Grup grup(User owner) {
        return Grup.builder().id(UUID.randomUUID()).name("Test").createdBy(owner).build();
    }

    private Grup grupAmAlumnes(User owner, User... students) {
        Grup g = Grup.builder().id(UUID.randomUUID()).name("Test")
                .createdBy(owner).students(new HashSet<>(Arrays.asList(students))).build();
        return g;
    }

    private User student() {
        return User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
    }

    private Exam exam(ExamStatus status) {
        Exam e = new Exam();
        e.setId(UUID.randomUUID());
        e.setStatus(status);
        return e;
    }

    private Exam examAmbModul(ExamStatus status) {
        Departament dep = Departament.builder().id(UUID.randomUUID()).nom("Dept").build();
        Cicle cicle = Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX").departament(dep).build();
        Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0483").nom("SI").cicle(cicle).build();
        Exam e = new Exam();
        e.setId(UUID.randomUUID());
        e.setStatus(status);
        e.setModul(modul);
        return e;
    }
}
