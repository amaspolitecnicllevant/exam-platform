package com.examplatform.domain.service;

import com.examplatform.domain.model.Cicle;
import com.examplatform.domain.model.Departament;
import com.examplatform.domain.model.Modul;
import com.examplatform.dto.CicleDto;
import com.examplatform.infrastructure.persistence.CicleRepository;
import com.examplatform.infrastructure.persistence.DepartamentRepository;
import com.examplatform.infrastructure.persistence.ModulRepository;
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
class CicleServiceTest {

    @Mock CicleRepository       cicleRepository;
    @Mock DepartamentRepository departamentRepository;
    @Mock ModulRepository       modulRepository;

    CicleService service;

    @BeforeEach
    void setUp() {
        service = new CicleService(cicleRepository, departamentRepository, modulRepository);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_codi_nou_desa_amb_codi_en_majuscules() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        // El servei crida findByCodi amb el codi original (minúscules); converteix a majúscules només en desar
        when(cicleRepository.findByCodi("asix")).thenReturn(Optional.empty());
        when(departamentRepository.findById(depId)).thenReturn(Optional.of(dep));
        Cicle saved = Cicle.builder().id(UUID.randomUUID()).codi("ASIX")
                .nom("Administració de Sistemes Informàtics en Xarxa").departament(dep).build();
        when(cicleRepository.save(any())).thenReturn(saved);

        CicleDto dto = service.create("asix", "Administració de Sistemes Informàtics en Xarxa", depId);

        assertThat(dto.codi()).isEqualTo("ASIX");
        verify(cicleRepository).save(argThat(c -> "ASIX".equals(c.getCodi())));
    }

    @Test
    void create_codi_duplicat_llanca_excepcio() {
        Cicle existent = Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX").build();
        when(cicleRepository.findByCodi("ASIX")).thenReturn(Optional.of(existent));

        assertThatThrownBy(() -> service.create("ASIX", "Altre nom", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASIX");
        verify(cicleRepository, never()).save(any());
    }

    @Test
    void create_departament_inexistent_llanca_excepcio() {
        UUID depId = UUID.randomUUID();
        when(cicleRepository.findByCodi("DAM")).thenReturn(Optional.empty());
        when(departamentRepository.findById(depId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("DAM", "Desenvolupament d'Aplicacions Multiplataforma", depId))
                .isInstanceOf(NoSuchElementException.class);
        verify(cicleRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sense_moduls_esborra_correctament() {
        UUID id = UUID.randomUUID();
        Cicle cicle = cicle(id);
        when(cicleRepository.findById(id)).thenReturn(Optional.of(cicle));
        when(modulRepository.findByCicleIdOrderByCodiAsc(id)).thenReturn(List.of());

        service.delete(id);

        verify(cicleRepository).delete(cicle);
    }

    @Test
    void delete_amb_moduls_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        Cicle cicle = cicle(id);
        Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0483")
                .nom("Sistemes Informàtics").cicle(cicle).build();
        when(cicleRepository.findById(id)).thenReturn(Optional.of(cicle));
        when(modulRepository.findByCicleIdOrderByCodiAsc(id)).thenReturn(List.of(modul));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mòduls");
        verify(cicleRepository, never()).delete(any());
    }

    @Test
    void delete_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(cicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── findByDepartament ─────────────────────────────────────────────────────

    @Test
    void findByDepartament_retorna_nomes_cicles_del_departament() {
        UUID depId = UUID.randomUUID();
        Departament dep = Departament.builder().id(depId).nom("Informàtica").build();
        List<Cicle> cicles = List.of(
                Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX").departament(dep).build(),
                Cicle.builder().id(UUID.randomUUID()).codi("DAM").nom("DAM").departament(dep).build()
        );
        when(cicleRepository.findByDepartamentIdOrderByCodiAsc(depId)).thenReturn(cicles);

        List<CicleDto> result = service.findByDepartament(depId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CicleDto::codi).containsExactly("ASIX", "DAM");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cicle cicle(UUID id) {
        Departament dep = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        return Cicle.builder().id(id).codi("ASIX").nom("ASIX").departament(dep).build();
    }
}
