package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.GrupRepository;
import com.examplatform.infrastructure.persistence.ImparticioRepository;
import com.examplatform.infrastructure.persistence.MatriculaRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlumnesVisiblesTest {

    @Mock ImparticioRepository imparticioRepository;
    @Mock MatriculaRepository matriculaRepository;
    @Mock GrupRepository grupRepository;
    @Mock UserRepository userRepository;

    AlumnesVisibles visibles;
    User prof = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();

    @BeforeEach
    void setUp() {
        visibles = new AlumnesVisibles(imparticioRepository, matriculaRepository, grupRepository, userRepository);
    }

    private static User alumne() {
        return User.builder().id(UUID.randomUUID()).name("A").role(Role.STUDENT).build();
    }

    private static Matricula matricula(User alumne) {
        return Matricula.builder().id(UUID.randomUUID()).alumne(alumne)
                .modul(Modul.builder().id(UUID.randomUUID()).build()).curs("2026-27").build();
    }

    @Test
    void l_administracio_ho_veu_tot_i_no_consulta_res() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();

        assertThat(visibles.idsVisibles(admin)).isNull();
        verifyNoInteractions(imparticioRepository, matriculaRepository, grupRepository, userRepository);
    }

    @Test
    void un_professor_veu_els_alumnes_dels_seus_moduls_els_dels_seus_grups_i_els_que_ha_creat() {
        User delModul = alumne(), delGrup = alumne(), creat = alumne(), altre = alumne();
        UUID modul = UUID.randomUUID();
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of(modul));
        when(matriculaRepository.findByModulIdsAmbDetall(List.of(modul))).thenReturn(List.of(matricula(delModul)));
        Grup grup = Grup.builder().id(UUID.randomUUID()).name("G").students(Set.of(delGrup)).build();
        when(grupRepository.findByCreatedByIdWithStudents(prof.getId())).thenReturn(List.of(grup));
        when(userRepository.findByCreatPer(prof.getId())).thenReturn(List.of(creat));

        Set<UUID> ids = visibles.idsVisibles(prof);

        assertThat(ids).containsExactlyInAnyOrder(delModul.getId(), delGrup.getId(), creat.getId());
        assertThat(ids).doesNotContain(altre.getId());
    }

    @Test
    void un_professor_sense_moduls_no_consulta_les_matricules() {
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of());
        when(grupRepository.findByCreatedByIdWithStudents(prof.getId())).thenReturn(List.of());
        when(userRepository.findByCreatPer(prof.getId())).thenReturn(List.of());

        assertThat(visibles.idsVisibles(prof)).isEmpty();
        verifyNoInteractions(matriculaRepository);
    }

    @Test
    void del_que_ha_creat_un_professor_nomes_compten_els_alumnes_no_altres_comptes() {
        User personal = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of());
        when(grupRepository.findByCreatedByIdWithStudents(prof.getId())).thenReturn(List.of());
        when(userRepository.findByCreatPer(prof.getId())).thenReturn(List.of(personal));

        assertThat(visibles.idsVisibles(prof)).isEmpty();
    }

    @Test
    void un_alumne_no_en_veu_cap() {
        User alumne = alumne();

        assertThat(visibles.idsVisibles(alumne)).isEmpty();
    }

    @Test
    void comprovar_alumnes_visibles_accepta_els_propis_i_rebutja_els_aliens() {
        User meu = alumne(), alie = alumne();
        when(imparticioRepository.findModulIdsByProfessorId(prof.getId())).thenReturn(List.of());
        when(grupRepository.findByCreatedByIdWithStudents(prof.getId())).thenReturn(List.of());
        when(userRepository.findByCreatPer(prof.getId())).thenReturn(List.of(meu));

        assertThatNoException().isThrownBy(() -> visibles.assertVisibles(prof, List.of(meu.getId())));
        assertThatThrownBy(() -> visibles.assertVisibles(prof, List.of(meu.getId(), alie.getId())))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("assignatures");
    }

    @Test
    void l_administracio_pot_triar_qualsevol_alumne() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();

        assertThatNoException().isThrownBy(() -> visibles.assertVisibles(admin, List.of(UUID.randomUUID())));
    }
}
