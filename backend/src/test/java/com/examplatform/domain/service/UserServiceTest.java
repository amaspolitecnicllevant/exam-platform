package com.examplatform.domain.service;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.dto.UserDto;
import com.examplatform.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final User ADMIN = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
    private static final User PROFESSOR = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();


    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.examplatform.infrastructure.persistence.ExamSessionRepository sessionRepository;
    @Mock com.examplatform.infrastructure.persistence.ExamRepository examRepository;
    @Mock com.examplatform.infrastructure.persistence.GrupRepository grupRepository;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, sessionRepository, examRepository, grupRepository);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    private User alumne() {
        User u = User.builder().id(UUID.randomUUID()).name("Anna").role(Role.STUDENT).build();
        org.mockito.Mockito.lenient().when(userRepository.findById(u.getId())).thenReturn(java.util.Optional.of(u));
        return u;
    }

    @Test
    void delete_usuari_sense_dades_l_elimina() {
        User u = alumne();

        service.delete(u.getId(), ADMIN);

        org.mockito.Mockito.verify(userRepository).delete(u);
    }

    @Test
    void delete_no_et_pots_eliminar_a_tu_mateix() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(ADMIN.getId(), ADMIN))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_alumne_amb_examens_fets_es_rebutja() {
        User u = alumne();
        org.mockito.Mockito.when(sessionRepository.existsByStudentId(u.getId())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(u.getId(), ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notes");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_professor_amb_examens_o_grups_es_rebutja() {
        User u = alumne();
        org.mockito.Mockito.when(grupRepository.existsByCreatedById(u.getId())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(u.getId(), ADMIN))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_email_nou_desa_usuari_amb_password_codificat() {
        when(userRepository.existsByEmail("nou@test.cat")).thenReturn(false);
        when(passwordEncoder.encode("contrasenya1")).thenReturn("hashed");
        User saved = User.builder().id(UUID.randomUUID())
                .name("Nou Usuari").email("nou@test.cat")
                .passwordHash("hashed").role(Role.STUDENT).build();
        when(userRepository.save(any())).thenReturn(saved);

        UserDto dto = service.create(new UserDto.CreateRequest("Nou Usuari", "nou@test.cat", "contrasenya1", Role.STUDENT), ADMIN);

        assertThat(dto.email()).isEqualTo("nou@test.cat");
        verify(passwordEncoder).encode("contrasenya1");
        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPasswordHash())));
    }

    @Test
    void create_email_duplicat_llanca_excepcio() {
        when(userRepository.existsByEmail("existent@test.cat")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new UserDto.CreateRequest("Test", "existent@test.cat", "pass", Role.STUDENT), ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existent@test.cat");
        verify(userRepository, never()).save(any());
    }

    // ── findAll / findByRole ──────────────────────────────────────────────────

    @Test
    void findAll_retorna_tots_els_usuaris() {
        List<User> users = List.of(
                user(Role.PROFESSOR), user(Role.STUDENT), user(Role.ADMIN));
        when(userRepository.findAll()).thenReturn(users);

        assertThat(service.findAll()).hasSize(3);
    }

    @Test
    void findByRole_retorna_nomes_usuaris_del_rol_indicat() {
        List<User> students = List.of(user(Role.STUDENT), user(Role.STUDENT));
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(students);

        List<UserDto> result = service.findByRole(Role.STUDENT);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(u -> u.role() == Role.STUDENT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID())
                .email(UUID.randomUUID() + "@test.cat")
                .name("Test").role(role).build();
    }

    // ── escalada de privilegis ────────────────────────────────────────────────

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Role.class, names = {"ADMIN", "PROFESSOR"})
    void professor_no_pot_crear_usuaris_que_no_siguin_alumnes(Role rol) {
        assertThatThrownBy(() -> service.create(
                new UserDto.CreateRequest("X", "x@test.cat", "contrasenya1", rol), PROFESSOR))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void professor_pot_crear_alumnes() {
        when(userRepository.existsByEmail("al@test.cat")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = service.create(new UserDto.CreateRequest("Al", "al@test.cat", "contrasenya1", Role.STUDENT), PROFESSOR);

        assertThat(dto.role()).isEqualTo(Role.STUDENT);
    }
}
