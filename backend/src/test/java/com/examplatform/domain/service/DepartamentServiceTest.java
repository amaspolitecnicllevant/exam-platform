package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.DepartamentDto;
import com.examplatform.dto.ProfessorDepartamentDto;
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
class DepartamentServiceTest {

    @Mock DepartamentRepository         departamentRepository;
    @Mock CicleRepository               cicleRepository;
    @Mock ProfessorDepartamentRepository profDepRepository;
    @Mock UserRepository                userRepository;

    DepartamentService service;

    @BeforeEach
    void setUp() {
        service = new DepartamentService(
                departamentRepository, cicleRepository, profDepRepository, userRepository);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_nom_nou_desa_i_retorna_dto() {
        when(departamentRepository.findByNom("Informàtica")).thenReturn(Optional.empty());
        Departament saved = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        when(departamentRepository.save(any())).thenReturn(saved);

        DepartamentDto dto = service.create("Informàtica");

        assertThat(dto.nom()).isEqualTo("Informàtica");
        verify(departamentRepository).save(any());
    }

    @Test
    void create_nom_duplicat_llanca_excepcio() {
        Departament existent = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        when(departamentRepository.findByNom("Informàtica")).thenReturn(Optional.of(existent));

        assertThatThrownBy(() -> service.create("Informàtica"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Informàtica");
        verify(departamentRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sense_cicles_esborra_correctament() {
        UUID id = UUID.randomUUID();
        Departament dep = Departament.builder().id(id).nom("Informàtica").build();
        when(departamentRepository.findById(id)).thenReturn(Optional.of(dep));
        when(cicleRepository.findByDepartamentIdOrderByCodiAsc(id)).thenReturn(List.of());

        service.delete(id);

        verify(departamentRepository).delete(dep);
    }

    @Test
    void delete_amb_cicles_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        Departament dep = Departament.builder().id(id).nom("Informàtica").build();
        Cicle cicle = Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX")
                .departament(dep).build();
        when(departamentRepository.findById(id)).thenReturn(Optional.of(dep));
        when(cicleRepository.findByDepartamentIdOrderByCodiAsc(id)).thenReturn(List.of(cicle));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cicles");
        verify(departamentRepository, never()).delete(any());
    }

    @Test
    void delete_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(departamentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── findProfessors ────────────────────────────────────────────────────────

    @Test
    void findProfessors_retorna_llista_del_departament() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        User prof = professor();
        ProfessorDepartament pd = ProfessorDepartament.builder()
                .professor(prof).departament(dep).esCap(false).build();
        when(profDepRepository.findByDepartamentId(depId)).thenReturn(List.of(pd));

        List<ProfessorDepartamentDto> result = service.findProfessors(depId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).professorId()).isEqualTo(prof.getId());
    }

    // ── addProfessor ──────────────────────────────────────────────────────────

    @Test
    void addProfessor_professor_valid_afegeix_correctament() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        User prof = professor();
        when(departamentRepository.findById(depId)).thenReturn(Optional.of(dep));
        when(userRepository.findById(prof.getId())).thenReturn(Optional.of(prof));
        when(profDepRepository.existsByProfessorIdAndDepartamentId(prof.getId(), depId))
                .thenReturn(false);
        ProfessorDepartament saved = ProfessorDepartament.builder()
                .professor(prof).departament(dep).esCap(true).build();
        when(profDepRepository.save(any())).thenReturn(saved);

        ProfessorDepartamentDto dto = service.addProfessor(depId, prof.getId(), true);

        assertThat(dto.esCap()).isTrue();
        verify(profDepRepository).save(any());
    }

    @Test
    void addProfessor_usuari_estudiant_llanca_excepcio() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        User student = User.builder().id(UUID.randomUUID())
                .email("s@test.cat").name("Alumne").role(Role.STUDENT).build();
        when(departamentRepository.findById(depId)).thenReturn(Optional.of(dep));
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.addProfessor(depId, student.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és professor");
        verify(profDepRepository, never()).save(any());
    }

    @Test
    void addProfessor_ja_pertany_llanca_excepcio() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        User prof = professor();
        when(departamentRepository.findById(depId)).thenReturn(Optional.of(dep));
        when(userRepository.findById(prof.getId())).thenReturn(Optional.of(prof));
        when(profDepRepository.existsByProfessorIdAndDepartamentId(prof.getId(), depId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addProfessor(depId, prof.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja pertany");
        verify(profDepRepository, never()).save(any());
    }

    @Test
    void addProfessor_departament_inexistent_llanca_excepcio() {
        UUID depId = UUID.randomUUID();
        when(departamentRepository.findById(depId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addProfessor(depId, UUID.randomUUID(), false))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addProfessor_usuari_inexistent_llanca_excepcio() {
        UUID depId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        when(departamentRepository.findById(depId)).thenReturn(Optional.of(dep));
        when(userRepository.findById(profId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addProfessor(depId, profId, false))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── removeProfessor ───────────────────────────────────────────────────────

    @Test
    void removeProfessor_membre_existent_es_treu() {
        UUID depId  = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        when(profDepRepository.existsByProfessorIdAndDepartamentId(profId, depId))
                .thenReturn(true);

        service.removeProfessor(depId, profId);

        verify(profDepRepository).deleteByProfessorIdAndDepartamentId(profId, depId);
    }

    @Test
    void removeProfessor_no_membre_llanca_excepcio() {
        UUID depId  = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        when(profDepRepository.existsByProfessorIdAndDepartamentId(profId, depId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeProfessor(depId, profId))
                .isInstanceOf(NoSuchElementException.class);
        verify(profDepRepository, never()).deleteByProfessorIdAndDepartamentId(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User professor() {
        return User.builder().id(UUID.randomUUID())
                .email("prof@test.cat").name("Professor").role(Role.PROFESSOR).build();
    }
}
