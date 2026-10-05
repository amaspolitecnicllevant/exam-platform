package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ImportacioDto;
import com.examplatform.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportacioUsuarisServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ModulRepository modulRepository;
    @Mock MatriculaRepository matriculaRepository;
    @Mock GrupRepository grupRepository;
    @Mock ImparticioRepository imparticioRepository;
    @Mock ConfiguracioService configuracioService;

    ImportacioUsuarisService service;
    final User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    final Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0483").nom("Sistemes").build();

    @BeforeEach
    void setUp() {
        service = new ImportacioUsuarisService(userRepository, passwordEncoder, modulRepository,
                matriculaRepository, grupRepository, imparticioRepository, configuracioService);
        ConfiguracioSistema c = new ConfiguracioSistema();
        c.setCursActiu("2026-27");
        lenient().when(configuracioService.get()).thenReturn(c);
        lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        lenient().when(passwordEncoder.encode(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        lenient().when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        lenient().when(grupRepository.save(any())).thenAnswer(inv -> {
            Grup g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
        lenient().when(modulRepository.findByCodi("0483")).thenReturn(Optional.of(modul));
    }

    private ImportacioDto importa(String csv, User qui) throws Exception {
        return service.importa(csv.getBytes(StandardCharsets.UTF_8), null, qui);
    }

    private List<User> usuarisDesats() {
        ArgumentCaptor<User> c = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(0)).save(c.capture());
        return c.getAllValues();
    }

    // ── Format ────────────────────────────────────────────────────────────────

    @Test
    void format_antic_en_angles_continua_funcionant() throws Exception {
        ImportacioDto r = importa("name,email,password,role\nAlumne U,a@test.cat,contrasenya1,STUDENT\n", admin);

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.errors()).isEmpty();
        assertThat(usuarisDesats()).singleElement().satisfies(u -> {
            assertThat(u.getEmail()).isEqualTo("a@test.cat");
            assertThat(u.getPasswordHash()).isEqualTo("hash:contrasenya1");
        });
    }

    @Test
    void sense_capcalera_s_enten_el_format_antic() throws Exception {
        ImportacioDto r = importa("Anna,anna@test.cat,contrasenya1,STUDENT\n", admin);
        assertThat(r.created()).isEqualTo(1);
    }

    @Test
    void capcalera_en_catala_amb_punt_i_coma_bom_i_columnes_en_un_altre_ordre() throws Exception {
        String csv = "﻿Correu;Nom;Mòdul\nanna@test.cat;Anna Puig;0483\n";
        ImportacioDto r = importa(csv, admin);

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.matriculats()).isEqualTo(1);
        assertThat(usuarisDesats()).singleElement().extracting(User::getName).isEqualTo("Anna Puig");
    }

    @Test
    void fitxer_en_windows_1252_conserva_els_accents() throws Exception {
        byte[] bytes = "nom;email\nJoan Martí;joan@test.cat\n".getBytes(Charset.forName("windows-1252"));

        service.importa(bytes, null, admin);

        assertThat(usuarisDesats()).singleElement().extracting(User::getName).isEqualTo("Joan Martí");
    }

    @Test
    void sense_columna_de_correu_es_rebutja_el_fitxer() {
        assertThatThrownBy(() -> importa("nom;telefon\nAnna;600\n", admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Validacions ───────────────────────────────────────────────────────────

    @Test
    void files_invalides_es_llisten_i_no_es_creen() throws Exception {
        String csv = "nom,email,contrasenya\n"
                + ",sense-nom@test.cat,contrasenya1\n"
                + "Sense Arrova,no-es-un-correu,contrasenya1\n"
                + "Curta,curta@test.cat,1234\n";
        ImportacioDto r = importa(csv, admin);

        assertThat(r.created()).isZero();
        assertThat(r.errors()).hasSize(3);
        assertThat(r.errors().get(2)).contains("8");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rol_desconegut_dona_error_i_continua() throws Exception {
        ImportacioDto r = importa("nom,email,contrasenya,rol\nA,a@test.cat,contrasenya1,DIRECTOR\nB,b@test.cat,contrasenya1,\n", admin);

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.errors()).singleElement().asString().contains("DIRECTOR");
    }

    @Test
    void professor_nomes_crea_alumnes() throws Exception {
        String csv = "name,email,password,role\nAl,al@test.cat,contrasenya1,STUDENT\n"
                + "Ad,ad@test.cat,contrasenya1,ADMIN\nPr,pr@test.cat,contrasenya1,PROFESSOR\n";
        ImportacioDto r = importa(csv, professor);

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.errors()).hasSize(2);
        assertThat(usuarisDesats()).allMatch(u -> u.getRole() == Role.STUDENT);
    }

    @Test
    void professor_no_pot_forcar_el_rol_admin() {
        assertThatThrownBy(() -> service.importa("n,a@t.cat".getBytes(), Role.ADMIN, professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Contrasenyes generades ────────────────────────────────────────────────

    @Test
    void contrasenya_en_blanc_en_genera_una_i_la_retorna_una_sola_vegada() throws Exception {
        ImportacioDto r = importa("nom,email,contrasenya\nAnna,anna@test.cat,\nBiel,biel@test.cat,contrasenya1\n", admin);

        assertThat(r.created()).isEqualTo(2);
        assertThat(r.contrasenyes()).singleElement().satisfies(c -> {
            assertThat(c.email()).isEqualTo("anna@test.cat");
            assertThat(c.contrasenya()).hasSize(10).matches("[a-z2-9]+").containsPattern("[2-9]");
        });
        String generada = r.contrasenyes().get(0).contrasenya();
        assertThat(usuarisDesats()).anyMatch(u -> u.getPasswordHash().equals("hash:" + generada));
    }

    @Test
    void les_contrasenyes_generades_son_diferents() {
        Set<String> vistes = new HashSet<>();
        for (int i = 0; i < 200; i++) vistes.add(ImportacioUsuarisService.generaContrasenya());
        assertThat(vistes).hasSize(200);
    }

    // ── Mòdul, curs i grup ────────────────────────────────────────────────────

    @Test
    void matricula_al_modul_amb_el_curs_actiu_o_el_de_la_columna() throws Exception {
        importa("nom,email,modul,curs\nAnna,anna@test.cat,0483,\nBiel,biel@test.cat,0483,2025-26\n", admin);

        ArgumentCaptor<Matricula> c = ArgumentCaptor.forClass(Matricula.class);
        verify(matriculaRepository, times(2)).save(c.capture());
        assertThat(c.getAllValues()).extracting(Matricula::getCurs).containsExactly("2026-27", "2025-26");
        assertThat(c.getAllValues()).allMatch(m -> m.getModul() == modul);
    }

    @Test
    void modul_inexistent_no_crea_el_compte() throws Exception {
        ImportacioDto r = importa("nom,email,modul\nAnna,anna@test.cat,9999\n", admin);

        assertThat(r.errors()).singleElement().asString().contains("9999");
        verify(userRepository, never()).save(any());
    }

    @Test
    void professor_nomes_matricula_als_moduls_que_imparteix() throws Exception {
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(false);

        ImportacioDto r = importa("nom,email,modul\nAnna,anna@test.cat,0483\n", professor);

        assertThat(r.errors()).singleElement().asString().contains("no imparteixes");
        verify(userRepository, never()).save(any());
        verify(matriculaRepository, never()).save(any());
    }

    @Test
    void alumne_existent_no_es_modifica_pero_es_matricula_i_s_afegeix_al_grup() throws Exception {
        User anna = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@test.cat")
                .passwordHash("antiga").role(Role.STUDENT).build();
        when(userRepository.findByEmail("anna@test.cat")).thenReturn(Optional.of(anna));
        Grup grup = Grup.builder().id(UUID.randomUUID()).name("1r DAW").createdBy(admin).build();
        when(grupRepository.findByCreatedByIdWithStudents(admin.getId())).thenReturn(List.of(grup));

        ImportacioDto r = importa("nom,email,contrasenya,modul,grup\nAnna,Anna@Test.cat,novacontra1,0483,1R DAW\n", admin);

        assertThat(r.created()).isZero();
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.matriculats()).isEqualTo(1);
        assertThat(r.afegitsAGrup()).isEqualTo(1);
        assertThat(anna.getPasswordHash()).isEqualTo("antiga");
        assertThat(grup.getStudents()).containsExactly(anna);
        verify(grupRepository).save(grup);
    }

    @Test
    void ja_matriculat_no_es_torna_a_matricular() throws Exception {
        when(matriculaRepository.existsByAlumneIdAndModulIdAndCurs(any(), eq(modul.getId()), eq("2026-27"))).thenReturn(true);

        ImportacioDto r = importa("nom,email,modul\nAnna,anna@test.cat,0483\n", admin);

        assertThat(r.matriculats()).isZero();
        verify(matriculaRepository, never()).save(any());
    }

    @Test
    void grup_inexistent_es_crea_una_sola_vegada_amb_el_modul() throws Exception {
        when(grupRepository.findByCreatedByIdWithStudents(admin.getId())).thenReturn(List.of());

        ImportacioDto r = importa("nom,email,modul,grup\nAnna,anna@test.cat,0483,2n SMX\nBiel,biel@test.cat,0483,2n smx\n", admin);

        assertThat(r.grupsCreats()).containsExactly("2n SMX");
        assertThat(r.afegitsAGrup()).isEqualTo(2);
        ArgumentCaptor<Grup> c = ArgumentCaptor.forClass(Grup.class);
        verify(grupRepository, atLeastOnce()).save(c.capture());
        Grup creat = c.getAllValues().get(0);
        assertThat(creat.getCreatedBy()).isSameAs(admin);
        assertThat(creat.getModul()).isSameAs(modul);
        assertThat(creat.getStudents()).hasSize(2);
    }

    @Test
    void no_es_pot_matricular_un_compte_que_no_es_d_alumne() throws Exception {
        User prof = User.builder().id(UUID.randomUUID()).email("prof@test.cat").role(Role.PROFESSOR).build();
        when(userRepository.findByEmail("prof@test.cat")).thenReturn(Optional.of(prof));

        ImportacioDto r = importa("nom,email,modul\nProf,prof@test.cat,0483\n", admin);

        assertThat(r.errors()).singleElement().asString().contains("no és d'alumne");
        verify(matriculaRepository, never()).save(any());
    }
}
