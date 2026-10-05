package com.examplatform.domain.service;

import com.examplatform.config.JwtUtil;
import com.examplatform.domain.model.*;
import com.examplatform.dto.*;
import com.examplatform.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitacioServiceTest {

    @Mock InvitacioRepository  invitacioRepository;
    @Mock ModulRepository      modulRepository;
    @Mock UserRepository       userRepository;
    @Mock MatriculaRepository  matriculaRepository;
    @Mock GrupRepository       grupRepository;
    @Mock PasswordEncoder      passwordEncoder;
    @Mock JwtUtil              jwtUtil;
    @Mock ImparticioRepository imparticioRepository;

    InvitacioService service;

    @BeforeEach
    void setUp() {
        service = new InvitacioService(invitacioRepository, modulRepository,
                userRepository, matriculaRepository, grupRepository, passwordEncoder, jwtUtil,
                imparticioRepository);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_genera_invitacio_amb_7_dies_de_validesa() {
        Modul modul = modul();
        User professor = professor();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);
        when(invitacioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvitacioDto dto = service.create(modul.getId(), "2026-27", null, null, professor);

        assertThat(dto.curs()).isEqualTo("2026-27");
        assertThat(dto.active()).isTrue();
        verify(invitacioRepository).save(argThat(i ->
                i.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6))));
    }

    @Test
    void create_per_a_un_modul_que_no_imparteix_llanca_AccessDenied() {
        Modul modul = modul();
        User professor = professor();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.create(modul.getId(), "2026-27", null, null, professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(invitacioRepository, never()).save(any());
    }

    @Test
    void create_amb_el_grup_d_un_altre_professor_llanca_AccessDenied() {
        Modul modul = modul();
        User professor = professor();
        Grup grupAlie = Grup.builder().id(UUID.randomUUID()).name("Aliè").createdBy(professor()).build();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);
        when(grupRepository.findById(grupAlie.getId())).thenReturn(Optional.of(grupAlie));

        assertThatThrownBy(() -> service.create(modul.getId(), "2026-27", null, grupAlie.getId(), professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(invitacioRepository, never()).save(any());
    }

    @Test
    void create_per_l_admin_no_requereix_imparticio() {
        Modul modul = modul();
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        when(invitacioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(modul.getId(), "2026-27", null, null, admin)).isNotNull();
        verifyNoInteractions(imparticioRepository);
    }

    // ── getPublica ────────────────────────────────────────────────────────────

    @Test
    void getPublica_retorna_info_si_link_valid() {
        Invitacio inv = invitacioValida(modul());
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        InvitacioPublicaDto dto = service.getPublica(inv.getToken());

        assertThat(dto.modulCodi()).isEqualTo("0483");
    }

    @Test
    void getPublica_link_caducat_llanca_excepcio() {
        Invitacio inv = invitacioValida(modul());
        inv.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.getPublica(inv.getToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caducat");
    }

    @Test
    void getPublica_link_desactivat_llanca_excepcio() {
        Invitacio inv = invitacioValida(modul());
        inv.setActive(false);
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.getPublica(inv.getToken()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── acceptar (nou usuari) ─────────────────────────────────────────────────

    @Test
    void acceptar_crea_alumne_nou_i_el_matricula() {
        Modul modul = modul();
        Invitacio inv = invitacioValida(modul);
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("nou@test.cat")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0); u.setId(UUID.randomUUID()); return u;
        });
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(any(), any(), any()))
                .thenReturn(false);
        when(matriculaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invitacioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(any())).thenReturn("jwt-token");

        var req = new AcceptarConvitRequest("Nou Alumne", "nou@test.cat", "password123");
        LoginResponse resp = service.acceptar(inv.getToken(), req);

        assertThat(resp.token()).isEqualTo("jwt-token");
        assertThat(resp.role()).isEqualTo(Role.STUDENT);
        verify(matriculaRepository).save(any());
    }

    @Test
    void acceptar_alumne_ja_existent_nomes_el_matricula() {
        Modul modul = modul();
        Invitacio inv = invitacioValida(modul);
        User alumneExistent = User.builder().id(UUID.randomUUID())
                .name("Existent").email("ex@test.cat").passwordHash("hash-ex").role(Role.STUDENT).build();

        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("ex@test.cat")).thenReturn(Optional.of(alumneExistent));
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(any(), any(), any()))
                .thenReturn(false);
        when(matriculaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invitacioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(any())).thenReturn("jwt-token");

        when(passwordEncoder.matches("la-seva-contrasenya", "hash-ex")).thenReturn(true);
        var req = new AcceptarConvitRequest("Existent", "ex@test.cat", "la-seva-contrasenya");
        service.acceptar(inv.getToken(), req);

        verify(userRepository, never()).save(any());
        verify(matriculaRepository).save(any());
    }

    @Test
    void acceptar_alumne_ja_matriculat_no_duplica_matricula() {
        Modul modul = modul();
        Invitacio inv = invitacioValida(modul);
        User alumne = User.builder().id(UUID.randomUUID())
                .name("Ja Matriculat").email("ja@test.cat").passwordHash("hash-ja").role(Role.STUDENT).build();

        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("ja@test.cat")).thenReturn(Optional.of(alumne));
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(any(), any(), any()))
                .thenReturn(true);
        when(invitacioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(any())).thenReturn("jwt-token");

        when(passwordEncoder.matches("contrasenya", "hash-ja")).thenReturn(true);
        var req = new AcceptarConvitRequest("Ja Matriculat", "ja@test.cat", "contrasenya");
        service.acceptar(inv.getToken(), req);

        verify(matriculaRepository, never()).save(any());
    }

    @Test
    void acceptar_email_de_professor_llanca_excepcio() {
        Invitacio inv = invitacioValida(modul());
        User prof = User.builder().id(UUID.randomUUID())
                .name("Prof").email("prof@test.cat").passwordHash("hash-prof").role(Role.PROFESSOR).build();

        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("prof@test.cat")).thenReturn(Optional.of(prof));
        when(passwordEncoder.matches("password123", "hash-prof")).thenReturn(true);

        var req = new AcceptarConvitRequest("Prof", "prof@test.cat", "password123");
        assertThatThrownBy(() -> service.acceptar(inv.getToken(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és d'alumne");
    }

    @Test
    void acceptar_alumne_existent_amb_contrasenya_incorrecta_no_dona_acces() {
        Invitacio inv = invitacioValida(modul());
        User victima = User.builder().id(UUID.randomUUID())
                .name("Víctima").email("victima@test.cat").passwordHash("hash-v").role(Role.STUDENT).build();
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("victima@test.cat")).thenReturn(Optional.of(victima));
        when(passwordEncoder.matches("endevinada", "hash-v")).thenReturn(false);

        var req = new AcceptarConvitRequest("Atacant", "victima@test.cat", "endevinada");
        assertThatThrownBy(() -> service.acceptar(inv.getToken(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contrasenya no és correcta");

        verify(jwtUtil, never()).generateToken(any());
        verify(matriculaRepository, never()).save(any());
        verify(invitacioRepository, never()).save(any());
    }

    @Test
    void acceptar_compte_de_google_sense_contrasenya_demana_iniciar_sessio() {
        Invitacio inv = invitacioValida(modul());
        User google = User.builder().id(UUID.randomUUID())
                .name("Google").email("g@test.cat").role(Role.STUDENT).build();
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("g@test.cat")).thenReturn(Optional.of(google));

        var req = new AcceptarConvitRequest("Google", "g@test.cat", "qualsevol1");
        // El mateix missatge que una contrasenya incorrecta: no revela que el compte és de Google
        assertThatThrownBy(() -> service.acceptar(inv.getToken(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(InvitacioService.CREDENCIALS_INCORRECTES);
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void acceptar_email_de_professor_amb_contrasenya_incorrecta_no_revela_el_rol() {
        Invitacio inv = invitacioValida(modul());
        User prof = User.builder().id(UUID.randomUUID())
                .name("Prof").email("p2@test.cat").passwordHash("hash-p2").role(Role.PROFESSOR).build();
        when(invitacioRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("p2@test.cat")).thenReturn(Optional.of(prof));
        when(passwordEncoder.matches("dolenta1", "hash-p2")).thenReturn(false);

        var req = new AcceptarConvitRequest("X", "p2@test.cat", "dolenta1");
        assertThatThrownBy(() -> service.acceptar(inv.getToken(), req))
                .hasMessageContaining("contrasenya no és correcta")
                .hasMessageNotContaining("alumne");
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    void deactivate_propietari_pot_desactivar() {
        User prof = professor();
        Invitacio inv = invitacioValida(modul());
        inv.setCreatedBy(prof);
        when(invitacioRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(invitacioRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.deactivate(inv.getId(), prof);

        verify(invitacioRepository).save(argThat(i -> !i.isActive()));
    }

    @Test
    void deactivate_altre_professor_llanca_excepcio() {
        User prof1 = professor();
        User prof2 = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        Invitacio inv = invitacioValida(modul());
        inv.setCreatedBy(prof1);
        when(invitacioRepository.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.deactivate(inv.getId(), prof2))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User professor() {
        return User.builder().id(UUID.randomUUID())
                .name("Prof").email("prof@test.cat").role(Role.PROFESSOR).build();
    }

    private Modul modul() {
        Departament dep = Departament.builder().id(UUID.randomUUID()).nom("Informàtica").build();
        Cicle cicle = Cicle.builder().id(UUID.randomUUID()).codi("ASIX")
                .nom("ASIX").departament(dep).build();
        return Modul.builder().id(UUID.randomUUID()).codi("0483")
                .nom("Sistemes Informàtics").cicle(cicle).build();
    }

    private Invitacio invitacioValida(Modul modul) {
        return Invitacio.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID())
                .modul(modul)
                .curs("2026-27")
                .createdBy(professor())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }
}
