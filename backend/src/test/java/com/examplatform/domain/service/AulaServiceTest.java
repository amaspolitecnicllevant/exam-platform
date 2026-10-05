package com.examplatform.domain.service;

import com.examplatform.domain.model.Aula;
import com.examplatform.dto.AulaDto;
import com.examplatform.infrastructure.persistence.AulaRepository;

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
class AulaServiceTest {

    @Mock AulaRepository aulaRepository;

    AulaService service;

    @BeforeEach
    void setUp() {
        service = new AulaService(aulaRepository);
    }

    @Test
    void create_aula_nova_ok() {
        when(aulaRepository.findByNom("A101")).thenReturn(Optional.empty());
        when(aulaRepository.save(any())).thenAnswer(inv -> {
            Aula a = inv.getArgument(0);
            a = Aula.builder().id(UUID.randomUUID())
                    .nom(a.getNom()).xarxaCidr(a.getXarxaCidr()).build();
            return a;
        });

        AulaDto result = service.create("A101", "10.0.1.0/24");

        assertThat(result.nom()).isEqualTo("A101");
        assertThat(result.xarxaCidr()).isEqualTo("10.0.1.0/24");
    }

    @Test
    void create_nom_duplicat_llanca_excepcio() {
        Aula existent = Aula.builder().id(UUID.randomUUID()).nom("A101").xarxaCidr("10.0.1.0/24").build();
        when(aulaRepository.findByNom("A101")).thenReturn(Optional.of(existent));

        assertThatThrownBy(() -> service.create("A101", "192.168.1.0/24"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A101");
    }

    @Test
    void update_aula_existent_actualitza() {
        UUID id = UUID.randomUUID();
        Aula aula = Aula.builder().id(id).nom("A101").xarxaCidr("10.0.1.0/24").build();
        when(aulaRepository.findById(id)).thenReturn(Optional.of(aula));
        when(aulaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AulaDto result = service.update(id, "B201", "192.168.2.0/24");

        assertThat(result.nom()).isEqualTo("B201");
        assertThat(result.xarxaCidr()).isEqualTo("192.168.2.0/24");
    }

    @Test
    void update_aula_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(aulaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, "B201", "192.168.2.0/24"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void delete_aula_existent_esborra() {
        UUID id = UUID.randomUUID();
        Aula aula = Aula.builder().id(id).nom("A101").xarxaCidr("10.0.1.0/24").build();
        when(aulaRepository.findById(id)).thenReturn(Optional.of(aula));

        service.delete(id);

        verify(aulaRepository).delete(aula);
    }

    @Test
    void delete_aula_inexistent_llanca_excepcio() {
        UUID id = UUID.randomUUID();
        when(aulaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NoSuchElementException.class);
        verify(aulaRepository, never()).delete(any());
    }

    @Test
    void findAll_retorna_ordenat_per_nom() {
        List<Aula> aules = List.of(
                Aula.builder().id(UUID.randomUUID()).nom("A101").xarxaCidr("10.0.1.0/24").build(),
                Aula.builder().id(UUID.randomUUID()).nom("B201").xarxaCidr("10.0.2.0/24").build()
        );
        when(aulaRepository.findAllByOrderByNomAsc()).thenReturn(aules);

        List<AulaDto> result = service.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).nom()).isEqualTo("A101");
        assertThat(result.get(1).nom()).isEqualTo("B201");
    }
}
