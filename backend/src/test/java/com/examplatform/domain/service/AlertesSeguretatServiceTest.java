package com.examplatform.domain.service;

import com.examplatform.domain.model.AuditLog;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.dto.AlertaDto;
import com.examplatform.infrastructure.persistence.AuditLogRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertesSeguretatServiceTest {

    static final LocalDateTime ARA = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;
    @Mock EquipsService equipsService;

    AlertesSeguretatService service;
    final List<AuditLog> fallits = new ArrayList<>();
    final List<AuditLog> altres = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AlertesSeguretatService(auditLogRepository, userRepository, equipsService);
        lenient().when(auditLogRepository.findByActionInAndCreatedAtAfterOrderByCreatedAtDesc(
                argThat(l -> l != null && l.contains("LOGIN_FALLIT")), any())).thenAnswer(inv -> List.copyOf(fallits));
        lenient().when(auditLogRepository.findByActionInAndCreatedAtAfterOrderByCreatedAtDesc(
                argThat(l -> l != null && l.contains("LOGIN_BLOQUEJAT")), any())).thenAnswer(inv -> List.copyOf(altres));
        lenient().when(equipsService.ordinadorsAlterats(any())).thenReturn(List.of());
    }

    private static AuditLog ev(String accio, String recurs, String ip, LocalDateTime quan, UUID usuari) {
        return AuditLog.builder().action(accio).resource(recurs).ipAddress(ip).createdAt(quan).userId(usuari).build();
    }

    private void fallitsDe(String compte, String ip, int n) {
        for (int i = 0; i < n; i++) fallits.add(ev("LOGIN_FALLIT", compte, ip, ARA.minusMinutes(i), null));
    }

    private List<AlertaDto> alertes() {
        return service.alertes(ARA);
    }

    // ── Inicis de sessió fallits ──────────────────────────────────────────────

    @Test
    void sense_esdeveniments_no_hi_ha_cap_alerta() {
        assertThat(alertes()).isEmpty();
    }

    @Test
    void la_finestra_dels_inicis_de_sessio_fallits_es_d_una_hora() {
        alertes();

        verify(auditLogRepository).findByActionInAndCreatedAtAfterOrderByCreatedAtDesc(List.of("LOGIN_FALLIT"), ARA.minusHours(1));
    }

    @Test
    void cinc_fallits_al_compte_d_un_alumne_donen_una_alerta_mitjana() {
        fallitsDe("anna@x.cat", "10.0.0.5", 5);
        when(userRepository.findByEmail("anna@x.cat")).thenReturn(Optional.of(
                User.builder().id(UUID.randomUUID()).email("anna@x.cat").role(Role.STUDENT).build()));

        List<AlertaDto> r = alertes();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).codi()).isEqualTo("ATAC_COMPTE");
        assertThat(r.get(0).gravetat()).isEqualTo("MITJANA");
        assertThat(r.get(0).quantitat()).isEqualTo(5);
        assertThat(r.get(0).titol()).contains("anna@x.cat");
    }

    @Test
    void quatre_fallits_no_arriben_al_llindar() {
        fallitsDe("anna@x.cat", "10.0.0.5", 4);

        assertThat(alertes()).isEmpty();
    }

    @Test
    void si_el_compte_atacat_es_del_personal_l_alerta_es_alta() {
        fallitsDe("prof@x.cat", "10.0.0.5", 5);
        when(userRepository.findByEmail("prof@x.cat")).thenReturn(Optional.of(
                User.builder().id(UUID.randomUUID()).email("prof@x.cat").role(Role.PROFESSOR).build()));

        assertThat(alertes()).extracting(AlertaDto::gravetat).containsExactly("ALTA");
    }

    @Test
    void un_compte_que_no_existeix_no_es_del_personal() {
        fallitsDe("inventat@x.cat", "10.0.0.5", 6);
        when(userRepository.findByEmail("inventat@x.cat")).thenReturn(Optional.empty());

        assertThat(alertes()).extracting(AlertaDto::gravetat).containsExactly("MITJANA");
    }

    @Test
    void deu_fallits_des_d_una_adreca_amb_pocs_comptes_son_mitjans() {
        fallitsDe("anna@x.cat", "10.0.0.9", 10);
        lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        List<AlertaDto> r = alertes().stream().filter(a -> a.codi().equals("ATAC_ADRECA")).toList();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).gravetat()).isEqualTo("MITJANA");
        assertThat(r.get(0).titol()).contains("10.0.0.9");
    }

    @Test
    void una_adreca_que_prova_molts_comptes_diferents_es_una_alerta_alta() {
        for (int i = 0; i < 12; i++) fallits.add(ev("LOGIN_FALLIT", "compte" + (i % 6) + "@x.cat", "10.0.0.9", ARA.minusMinutes(i), null));
        lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        AlertaDto a = alertes().stream().filter(x -> x.codi().equals("ATAC_ADRECA")).findFirst().orElseThrow();

        assertThat(a.gravetat()).isEqualTo("ALTA");
        assertThat(a.detall()).contains("6 comptes");
    }

    // ── Altres esdeveniments, cadascun amb la seva finestra ───────────────────

    @Test
    void els_bloquejos_de_les_ultimes_24_hores_compten_i_els_mes_antics_no() {
        altres.add(ev("LOGIN_BLOQUEJAT", "prof@x.cat", "10.0.0.5", ARA.minusHours(2), null));
        altres.add(ev("LOGIN_BLOQUEJAT", "prof@x.cat", "10.0.0.6", ARA.minusHours(30), null));

        List<AlertaDto> r = alertes();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).codi()).isEqualTo("COMPTES_BLOQUEJATS");
        assertThat(r.get(0).quantitat()).isEqualTo(1);
    }

    @Test
    void un_administrador_des_d_una_adreca_nova_surt_amb_el_seu_nom_durant_7_dies() {
        UUID admin = UUID.randomUUID();
        altres.add(ev("LOGIN_NOVA_IP", "192.168.1.50", "192.168.1.50", ARA.minusDays(6), admin));
        altres.add(ev("LOGIN_NOVA_IP", "8.8.8.8", "8.8.8.8", ARA.minusDays(8), admin));
        when(userRepository.findById(admin)).thenReturn(Optional.of(User.builder().id(admin).name("Maria Admin").role(Role.ADMIN).build()));

        List<AlertaDto> r = alertes();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).codi()).isEqualTo("ADMIN_NOVA_ADRECA");
        assertThat(r.get(0).detall()).contains("Maria Admin").contains("192.168.1.50").doesNotContain("8.8.8.8");
    }

    @Test
    void informes_d_ordinadors_rebutjats_i_restauracions_fallides() {
        altres.add(ev("EQUIP_TESTIMONI_INVALID", "nom=pc1", "10.0.0.7", ARA.minusHours(1), null));
        altres.add(ev("EQUIP_IP_FORA_D_AULA", "nom=pc2", "172.16.0.3", ARA.minusHours(3), null));
        altres.add(ev("EQUIP_RESTAURACIO_FALLIDA", "ordinador pc1 (A101): l'script ha fallat", null, ARA.minusHours(4), null));

        List<AlertaDto> r = alertes();

        assertThat(r).extracting(AlertaDto::codi).containsExactlyInAnyOrder("INFORMES_TESTIMONI", "INFORMES_FORA_AULA", "RESTAURACIONS_FALLIDES");
        assertThat(r.stream().filter(a -> a.codi().equals("INFORMES_FORA_AULA")).findFirst().orElseThrow().gravetat()).isEqualTo("INFO");
    }

    @Test
    void tres_canvis_de_contrasenya_o_sessions_en_una_hora_fan_saltar_l_alerta_i_dos_no() {
        UUID admin = UUID.randomUUID();
        when(userRepository.findById(admin)).thenReturn(Optional.of(User.builder().id(admin).name("Maria").role(Role.ADMIN).build()));
        altres.add(ev("PASSWORD_RESET", "u1", null, ARA.minusMinutes(10), admin));
        altres.add(ev("SESSIONS_REVOCADES", "u2", null, ARA.minusMinutes(20), admin));

        assertThat(alertes()).isEmpty();

        altres.add(ev("PASSWORD_RESET", "u3", null, ARA.minusMinutes(30), admin));

        List<AlertaDto> r = alertes();
        assertThat(r).hasSize(1);
        assertThat(r.get(0).codi()).isEqualTo("MOLTS_CANVIS_DE_CONTRASENYA");
        assertThat(r.get(0).quantitat()).isEqualTo(3);
    }

    @Test
    void uns_canvis_de_contrasenya_de_fa_mes_d_una_hora_no_compten() {
        UUID admin = UUID.randomUUID();
        for (int i = 0; i < 5; i++) altres.add(ev("PASSWORD_RESET", "u" + i, null, ARA.minusHours(3), admin));

        assertThat(alertes()).isEmpty();
    }

    // ── Ordinadors i ordre ────────────────────────────────────────────────────

    @Test
    void els_ordinadors_alterats_donen_una_alerta_alta_amb_els_seus_noms() {
        when(equipsService.ordinadorsAlterats(ARA)).thenReturn(List.of("A101/pc3", "A101/pc7"));

        List<AlertaDto> r = alertes();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).codi()).isEqualTo("ORDINADORS_ALTERATS");
        assertThat(r.get(0).gravetat()).isEqualTo("ALTA");
        assertThat(r.get(0).quantitat()).isEqualTo(2);
        assertThat(r.get(0).detall()).contains("A101/pc3").contains("A101/pc7");
    }

    @Test
    void les_alertes_van_ordenades_de_mes_a_menys_greu() {
        altres.add(ev("EQUIP_IP_FORA_D_AULA", "nom=pc2", "172.16.0.3", ARA.minusHours(3), null));          // INFO
        altres.add(ev("LOGIN_BLOQUEJAT", "prof@x.cat", "10.0.0.5", ARA.minusHours(2), null));              // MITJANA
        when(equipsService.ordinadorsAlterats(ARA)).thenReturn(List.of("A101/pc3"));                        // ALTA

        assertThat(alertes()).extracting(AlertaDto::gravetat).containsExactly("ALTA", "MITJANA", "INFO");
    }
}
