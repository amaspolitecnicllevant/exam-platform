package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ImparticioDto;
import com.examplatform.dto.ModulDto;
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
class ModulServiceTest {

    @Mock ModulRepository      modulRepository;
    @Mock CicleRepository      cicleRepository;
    @Mock ImparticioRepository imparticioRepository;
    @Mock UserRepository       userRepository;

    ModulService service;

    @BeforeEach
    void setUp() {
        service = new ModulService(modulRepository, cicleRepository,
                imparticioRepository, userRepository);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_codi_nou_desa_modul() {
        Cicle cicle = cicle();
        when(modulRepository.findByCodi("0483")).thenReturn(Optional.empty());
        when(cicleRepository.findById(cicle.getId())).thenReturn(Optional.of(cicle));
        when(modulRepository.save(any())).thenAnswer(inv -> {
            Modul m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ModulDto dto = service.create("0483", "Sistemes Informàtics", cicle.getId());

        assertThat(dto.codi()).isEqualTo("0483");
    }

    @Test
    void create_codi_duplicat_llanca_excepcio() {
        Modul existent = modul();
        when(modulRepository.findByCodi(existent.getCodi())).thenReturn(Optional.of(existent));

        assertThatThrownBy(() -> service.create(existent.getCodi(), "nom", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(existent.getCodi());
    }

    // ── addImparticio ─────────────────────────────────────────────────────────

    @Test
    void addImparticio_professor_valid_crea_imparticio() {
        Modul modul = modul();
        User professor = professor();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(userRepository.findById(professor.getId())).thenReturn(Optional.of(professor));
        when(imparticioRepository.existsByProfessorIdAndModulIdAndCurs(
                professor.getId(), modul.getId(), "2026-27")).thenReturn(false);
        when(imparticioRepository.save(any())).thenAnswer(inv -> {
            Imparticio i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });

        ImparticioDto dto = service.addImparticio(modul.getId(), professor.getId(), "2026-27");

        assertThat(dto.curs()).isEqualTo("2026-27");
        assertThat(dto.professorId()).isEqualTo(professor.getId());
    }

    @Test
    void addImparticio_duplicat_llanca_excepcio() {
        Modul modul = modul();
        User professor = professor();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(userRepository.findById(professor.getId())).thenReturn(Optional.of(professor));
        when(imparticioRepository.existsByProfessorIdAndModulIdAndCurs(
                professor.getId(), modul.getId(), "2026-27")).thenReturn(true);

        assertThatThrownBy(() ->
                service.addImparticio(modul.getId(), professor.getId(), "2026-27"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja imparteix");
    }

    @Test
    void addImparticio_usuari_no_professor_llanca_excepcio() {
        Modul modul = modul();
        User estudiant = User.builder().id(UUID.randomUUID())
                .name("Est").email("est@test.cat").role(Role.STUDENT).build();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(userRepository.findById(estudiant.getId())).thenReturn(Optional.of(estudiant));

        assertThatThrownBy(() ->
                service.addImparticio(modul.getId(), estudiant.getId(), "2026-27"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és professor");
    }

    // ── removeImparticio ──────────────────────────────────────────────────────

    @Test
    void removeImparticio_existent_elimina() {
        Imparticio imp = Imparticio.builder().id(UUID.randomUUID())
                .professor(professor()).modul(modul()).curs("2026-27").build();
        when(imparticioRepository.findById(imp.getId())).thenReturn(Optional.of(imp));

        service.removeImparticio(imp.getId());

        verify(imparticioRepository).delete(imp);
    }

    @Test
    void removeImparticio_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(imparticioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeImparticio(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Departament departament() {
        return Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
    }

    private Cicle cicle() {
        return Cicle.builder().id(UUID.randomUUID()).codi("ASIX")
                .nom("Administració de Sistemes").departament(departament()).build();
    }

    private Modul modul() {
        return Modul.builder().id(UUID.randomUUID()).codi("0483")
                .nom("Sistemes Informàtics").cicle(cicle()).build();
    }

    private User professor() {
        return User.builder().id(UUID.randomUUID())
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build();
    }
}
