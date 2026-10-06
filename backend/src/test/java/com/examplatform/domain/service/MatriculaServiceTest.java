package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.MatriculaDto;
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
class MatriculaServiceTest {

    @Mock MatriculaRepository matriculaRepository;
    @Mock UserRepository      userRepository;
    @Mock ModulRepository     modulRepository;
    @Mock ImparticioRepository imparticioRepository;

    MatriculaService service;

    @BeforeEach
    void setUp() {
        service = new MatriculaService(matriculaRepository, userRepository, modulRepository, imparticioRepository);
    }

    // ── enroll ────────────────────────────────────────────────────────────────

    @Test
    void enroll_alumne_i_modul_valids_crea_matricula() {
        User alumne = alumne();
        Modul modul = modul();
        when(userRepository.findById(alumne.getId())).thenReturn(Optional.of(alumne));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(
                alumne.getId(), modul.getId(), "2026-27")).thenReturn(false);
        when(matriculaRepository.save(any())).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MatriculaDto dto = service.enroll(alumne.getId(), modul.getId(), "2026-27");

        assertThat(dto.alumneId()).isEqualTo(alumne.getId());
        assertThat(dto.modulId()).isEqualTo(modul.getId());
        assertThat(dto.curs()).isEqualTo("2026-27");
    }

    @Test
    void enroll_duplicat_llanca_excepcio() {
        User alumne = alumne();
        Modul modul = modul();
        when(userRepository.findById(alumne.getId())).thenReturn(Optional.of(alumne));
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(
                alumne.getId(), modul.getId(), "2026-27")).thenReturn(true);

        assertThatThrownBy(() -> service.enroll(alumne.getId(), modul.getId(), "2026-27"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja està matriculat");
    }

    @Test
    void enroll_usuari_no_alumne_llanca_excepcio() {
        User professor = User.builder().id(UUID.randomUUID())
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build();
        when(userRepository.findById(professor.getId())).thenReturn(Optional.of(professor));

        assertThatThrownBy(() -> service.enroll(professor.getId(), UUID.randomUUID(), "2026-27"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és alumne");
    }

    @Test
    void enroll_alumne_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(id, UUID.randomUUID(), "2026-27"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── unenroll ──────────────────────────────────────────────────────────────

    @Test
    void unenroll_existent_elimina_matricula() {
        Matricula m = Matricula.builder().id(UUID.randomUUID())
                .alumne(alumne()).modul(modul()).curs("2026-27").build();
        when(matriculaRepository.findById(m.getId())).thenReturn(Optional.of(m));

        service.unenroll(m.getId());

        verify(matriculaRepository).delete(m);
    }

    @Test
    void unenroll_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(matriculaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unenroll(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── findByAlumne ──────────────────────────────────────────────────────────

    @Test
    void findByAlumne_retorna_matricules_de_lalumne() {
        User alumne = alumne();
        Modul modul = modul();
        Matricula m = Matricula.builder().id(UUID.randomUUID())
                .alumne(alumne).modul(modul).curs("2026-27").build();
        when(matriculaRepository.findByAlumneId(alumne.getId())).thenReturn(List.of(m));

        List<MatriculaDto> result = service.findByAlumne(alumne.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).curs()).isEqualTo("2026-27");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User alumne() {
        return User.builder().id(UUID.randomUUID())
                .name("Alumne").email("alumne@test.cat").role(Role.STUDENT).build();
    }

    private Modul modul() {
        Departament dep = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        Cicle cicle = Cicle.builder().id(UUID.randomUUID()).codi("ASIX")
                .nom("Administració de Sistemes").departament(dep).build();
        return Modul.builder().id(UUID.randomUUID()).codi("0483")
                .nom("Sistemes Informàtics").cicle(cicle).build();
    }

    // ── enrollLot ─────────────────────────────────────────────────────────────

    @Test
    void enrollLot_matricula_els_nous_i_compta_els_que_ja_hi_eren() {
        com.examplatform.domain.model.Modul m = com.examplatform.domain.model.Modul.builder()
                .id(UUID.randomUUID()).codi("0483").nom("SI").build();
        com.examplatform.domain.model.User a = com.examplatform.domain.model.User.builder().id(UUID.randomUUID())
                .name("A").role(com.examplatform.domain.model.Role.STUDENT).build();
        com.examplatform.domain.model.User b = com.examplatform.domain.model.User.builder().id(UUID.randomUUID())
                .name("B").role(com.examplatform.domain.model.Role.STUDENT).build();
        when(modulRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(userRepository.findAllById(any())).thenReturn(List.of(a, b));
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(a.getId(), m.getId(), "2026-27")).thenReturn(true);
        when(matriculaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = service.enrollLot(List.of(a.getId(), b.getId()), m.getId(), "2026-27");

        assertThat(r.jaMatriculats()).isEqualTo(1);
        assertThat(r.matriculades()).singleElement().satisfies(d -> assertThat(d.alumneId()).isEqualTo(b.getId()));
    }

    @Test
    void enrollLot_si_algun_no_es_alumne_no_matricula_ningu() {
        com.examplatform.domain.model.Modul m = com.examplatform.domain.model.Modul.builder()
                .id(UUID.randomUUID()).codi("0483").nom("SI").build();
        com.examplatform.domain.model.User a = com.examplatform.domain.model.User.builder().id(UUID.randomUUID())
                .role(com.examplatform.domain.model.Role.STUDENT).build();
        com.examplatform.domain.model.User p = com.examplatform.domain.model.User.builder().id(UUID.randomUUID())
                .email("p@x.cat").role(com.examplatform.domain.model.Role.PROFESSOR).build();
        when(modulRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(userRepository.findAllById(any())).thenReturn(List.of(a, p));

        assertThatThrownBy(() -> service.enrollLot(List.of(a.getId(), p.getId()), m.getId(), "2026-27"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(matriculaRepository, never()).save(any());
    }

    @Test
    void enrollLot_alumne_inexistent_es_rebutja() {
        com.examplatform.domain.model.Modul m = com.examplatform.domain.model.Modul.builder()
                .id(UUID.randomUUID()).codi("0483").nom("SI").build();
        when(modulRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.enrollLot(List.of(UUID.randomUUID()), m.getId(), "2026-27"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ── findVisibles ──────────────────────────────────────────────────────────

    private Matricula matricula(User alumne, Modul modul, String curs) {
        return Matricula.builder().id(UUID.randomUUID()).alumne(alumne).modul(modul).curs(curs).build();
    }

    private User usuari(Role rol) {
        return User.builder().id(UUID.randomUUID()).name("U").email("u@test.cat").role(rol).build();
    }

    @Test
    void findVisibles_administrador_veu_totes_les_matricules() {
        Modul m1 = modul();
        Modul m2 = modul();
        List<Matricula> totes = List.of(matricula(alumne(), m1, "2026-27"), matricula(alumne(), m2, "2026-27"));
        when(matriculaRepository.findAllAmbDetall()).thenReturn(totes);

        var r = service.findVisibles(usuari(Role.ADMIN));

        assertThat(r).hasSize(2);
        verifyNoInteractions(imparticioRepository);
    }

    @Test
    void findVisibles_professor_nomes_veu_els_moduls_que_imparteix() {
        User prof = usuari(Role.PROFESSOR);
        Modul meu = modul();
        User a = alumne();
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of(meu.getId()));
        when(matriculaRepository.findByModulIdsAmbDetall(List.of(meu.getId())))
                .thenReturn(List.of(matricula(a, meu, "2026-27")));

        var r = service.findVisibles(prof);

        assertThat(r).singleElement().satisfies(d -> {
            assertThat(d.alumneId()).isEqualTo(a.getId());
            assertThat(d.modulId()).isEqualTo(meu.getId());
        });
        // Mai es consulten totes les matrícules per a un professor
        verify(matriculaRepository, never()).findAllAmbDetall();
    }

    @Test
    void findVisibles_professor_sense_moduls_no_veu_res_ni_consulta_matricules() {
        User prof = usuari(Role.PROFESSOR);
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of());

        assertThat(service.findVisibles(prof)).isEmpty();
        verifyNoInteractions(matriculaRepository);
    }

    @Test
    void findVisibles_un_alumne_no_veu_cap_matricula() {
        assertThat(service.findVisibles(usuari(Role.STUDENT))).isEmpty();
        verifyNoInteractions(matriculaRepository, imparticioRepository);
    }
}
