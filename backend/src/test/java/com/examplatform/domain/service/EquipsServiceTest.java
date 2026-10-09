package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.EquipsService.Informe;
import com.examplatform.dto.EquipsDto;
import com.examplatform.dto.EquipsDto.Estat;
import com.examplatform.infrastructure.persistence.AulaRepository;
import com.examplatform.infrastructure.persistence.EquipAulaRepository;
import com.examplatform.infrastructure.persistence.EquipsReferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipsServiceTest {

    static final String TOKEN = "testimoni-secret";
    static final LocalDateTime ARA = LocalDateTime.of(2026, 10, 9, 12, 0);
    static final String LLISTA = "abc123  etc/pam.d/lightdm\ndef456  etc/examen/usuaris.d/examen\nexamen:x:1003:1003::/home/examen:/usr/sbin/nologin";

    @Mock AulaRepository aulaRepository;
    @Mock EquipAulaRepository equipRepository;
    @Mock EquipsReferenciaRepository referenciaRepository;
    @Mock AuditLogService auditLog;

    EquipsService service;
    Aula aula;
    User admin;

    @BeforeEach
    void setUp() {
        service = new EquipsService(aulaRepository, equipRepository, referenciaRepository, auditLog, TOKEN, 45, 7, 2);
        aula = Aula.builder().id(UUID.randomUUID()).nom("A101").xarxaCidr("10.100.94.0/24").build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
    }

    private static Informe informe(String nom, String integritat) {
        return new Informe(nom, integritat, true, true, "Firefox 155", 20000, 3600L, 0);
    }

    private void aulaConeguda() {
        when(aulaRepository.findAll()).thenReturn(List.of(aula));
    }

    private EquipAula desat() {
        ArgumentCaptor<EquipAula> c = ArgumentCaptor.forClass(EquipAula.class);
        verify(equipRepository).save(c.capture());
        return c.getValue();
    }

    // ── Recepció d'informes: seguretat ────────────────────────────────────────

    @Test
    void sense_testimoni_configurat_la_recepcio_esta_desactivada() {
        EquipsService off = new EquipsService(aulaRepository, equipRepository, referenciaRepository, auditLog, "", 45, 7, 2);

        assertThatThrownBy(() -> off.registraInforme(TOKEN, "10.100.94.19", informe("pc19", LLISTA), ARA))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no està activada");
        verifyNoInteractions(equipRepository);
    }

    @Test
    void un_testimoni_incorrecte_o_absent_es_rebutja() {
        assertThatThrownBy(() -> service.registraInforme("un-altre", "10.100.94.19", informe("pc19", LLISTA), ARA))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Testimoni");
        assertThatThrownBy(() -> service.registraInforme(null, "10.100.94.19", informe("pc19", LLISTA), ARA))
                .isInstanceOf(ResponseStatusException.class);
        verify(equipRepository, never()).save(any());
    }

    @Test
    void una_ip_que_no_es_de_cap_aula_es_rebutja() {
        aulaConeguda();

        assertThatThrownBy(() -> service.registraInforme(TOKEN, "192.168.1.5", informe("pc19", LLISTA), ARA))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no pertany a cap aula");
        verify(equipRepository, never()).save(any());
    }

    @Test
    void el_nom_de_l_ordinador_ha_de_ser_un_nom_segur() {
        for (String dolent : new String[]{null, "", "pc 19", "pc;rm", "<script>", "../x", "a".repeat(101), "-pc"}) {
            assertThatThrownBy(() -> service.registraInforme(TOKEN, "10.100.94.19", informe(dolent, LLISTA), ARA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(equipRepository, never()).save(any());
    }

    @Test
    void una_llista_d_integritat_massa_llarga_es_rebutja() {
        String gran = "x".repeat(EquipsService.MAX_INTEGRITAT + 1);

        assertThatThrownBy(() -> service.registraInforme(TOKEN, "10.100.94.19", informe("pc19", gran), ARA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("massa llarga");
    }

    // ── Recepció d'informes: dades ────────────────────────────────────────────

    @Test
    void un_ordinador_nou_es_crea_a_l_aula_de_la_seva_ip_amb_les_dades_de_l_informe() {
        aulaConeguda();
        when(equipRepository.findByAulaIdAndNom(aula.getId(), "pc19")).thenReturn(Optional.empty());
        when(equipRepository.countByAulaId(aula.getId())).thenReturn(0L);

        service.registraInforme(TOKEN, "10.100.94.19", informe("pc19", LLISTA), ARA);

        EquipAula e = desat();
        assertThat(e.getAula()).isSameAs(aula);
        assertThat(e.getNom()).isEqualTo("pc19");
        assertThat(e.getIp()).isEqualTo("10.100.94.19");
        assertThat(e.getDarrerInforme()).isEqualTo(ARA);
        assertThat(e.getNavegador()).isEqualTo("Firefox 155");
        assertThat(e.getDiscLliureMb()).isEqualTo(20000);
        assertThat(e.getArrencada()).isEqualTo(ARA.minusSeconds(3600));
        assertThat(e.getIntegritatResum()).hasSize(64);
    }

    @Test
    void un_segon_informe_del_mateix_ordinador_l_actualitza_en_lloc_de_duplicar_lo() {
        aulaConeguda();
        EquipAula existent = EquipAula.builder().aula(aula).nom("pc19").ip("10.100.94.19").darrerInforme(ARA.minusMinutes(15)).build();
        when(equipRepository.findByAulaIdAndNom(aula.getId(), "pc19")).thenReturn(Optional.of(existent));

        service.registraInforme(TOKEN, "10.100.94.20", informe("pc19", LLISTA), ARA);

        assertThat(desat()).isSameAs(existent);
        assertThat(existent.getDarrerInforme()).isEqualTo(ARA);
        assertThat(existent.getIp()).isEqualTo("10.100.94.20");
        verify(equipRepository, never()).countByAulaId(any());
    }

    @Test
    void una_aula_amb_el_maxim_d_ordinadors_no_en_accepta_de_nous_pero_si_actualitzacions() {
        aulaConeguda();
        when(equipRepository.findByAulaIdAndNom(aula.getId(), "nou")).thenReturn(Optional.empty());
        when(equipRepository.countByAulaId(aula.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.registraInforme(TOKEN, "10.100.94.30", informe("nou", LLISTA), ARA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("màxim");
        verify(equipRepository, never()).save(any());
    }

    @Test
    void si_una_ip_cau_en_dues_aules_es_tria_la_mes_especifica() {
        Aula ampla = Aula.builder().id(UUID.randomUUID()).nom("Totes").xarxaCidr("10.100.0.0/16").build();
        when(aulaRepository.findAll()).thenReturn(List.of(ampla, aula));
        when(equipRepository.findByAulaIdAndNom(aula.getId(), "pc19")).thenReturn(Optional.empty());
        when(equipRepository.countByAulaId(aula.getId())).thenReturn(0L);

        service.registraInforme(TOKEN, "10.100.94.19", informe("pc19", LLISTA), ARA);

        assertThat(desat().getAula()).isSameAs(aula);
    }

    @Test
    void la_llista_amb_altres_salts_de_linia_o_espais_al_final_dona_el_mateix_resum() {
        assertThat(EquipsService.resum(EquipsService.normalitza("a  x\r\nb  y\r\n")))
                .isEqualTo(EquipsService.resum(EquipsService.normalitza("a  x   \nb  y\n\n\n")));
        assertThat(EquipsService.resum(EquipsService.normalitza("a  x\nb  y")))
                .isNotEqualTo(EquipsService.resum(EquipsService.normalitza("a  x\nb  z")));
    }

    @Test
    void les_dades_opcionals_poden_faltar() {
        aulaConeguda();
        when(equipRepository.findByAulaIdAndNom(aula.getId(), "pc19")).thenReturn(Optional.empty());
        when(equipRepository.countByAulaId(aula.getId())).thenReturn(0L);

        service.registraInforme(TOKEN, "10.100.94.19", new Informe("pc19", null, null, null, null, null, null, null), ARA);

        EquipAula e = desat();
        assertThat(e.getIntegritat()).isEmpty();
        assertThat(e.getArrencada()).isNull();
        assertThat(e.getArribaPlataforma()).isNull();
    }

    // ── Estat ─────────────────────────────────────────────────────────────────

    private EquipAula equip(String nom, String integritat, LocalDateTime darrer, Boolean plataforma) {
        String n = EquipsService.normalitza(integritat);
        return EquipAula.builder().id(UUID.randomUUID()).aula(aula).nom(nom).ip("10.100.94.1").darrerInforme(darrer)
                .integritat(n).integritatResum(EquipsService.resum(n)).arribaPlataforma(plataforma).arribaIsard(true)
                .navegador("Firefox").discLliureMb(50000).usuarisDins(0).build();
    }

    private EquipsReferencia referencia(String integritat) {
        String n = EquipsService.normalitza(integritat);
        return EquipsReferencia.builder().id(EquipsReferencia.ID).integritat(n).integritatResum(EquipsService.resum(n))
                .origenNom("pc19").fixadaEl(ARA.minusDays(1)).build();
    }

    private EquipsDto consulta(EquipsReferencia ref, EquipAula... equips) {
        when(aulaRepository.existsById(aula.getId())).thenReturn(true);
        when(equipRepository.findByAulaIdOrderByNomAsc(aula.getId())).thenReturn(List.of(equips));
        when(referenciaRepository.findById(EquipsReferencia.ID)).thenReturn(Optional.ofNullable(ref));
        return service.equipsDeLAula(aula.getId(), ARA);
    }

    @Test
    void un_ordinador_igual_a_la_referencia_que_arriba_a_la_plataforma_esta_preparat() {
        EquipsDto d = consulta(referencia(LLISTA), equip("pc1", LLISTA, ARA.minusMinutes(5), true));

        assertThat(d.equips().get(0).estat()).isEqualTo(Estat.PREPARAT);
        assertThat(d.preparats()).isEqualTo(1);
        assertThat(d.referencia().origenNom()).isEqualTo("pc19");
    }

    @Test
    void una_configuracio_diferent_de_la_referencia_es_alterada_i_diu_que_ha_canviat() {
        String alterada = "abc123  etc/pam.d/lightdm\nzzz999  etc/examen/usuaris.d/examen\nexamen:x:1003:1003::/home/examen:/usr/sbin/nologin\n0f0f0f  etc/lightdm/lightdm.conf.d/autologin.conf";

        EquipsDto d = consulta(referencia(LLISTA), equip("pc1", alterada, ARA.minusMinutes(5), true));

        EquipsDto.Equip e = d.equips().get(0);
        assertThat(e.estat()).isEqualTo(Estat.ALTERAT);
        assertThat(d.alterats()).isEqualTo(1);
        assertThat(e.diferencies()).contains("+ zzz999  etc/examen/usuaris.d/examen",
                "+ 0f0f0f  etc/lightdm/lightdm.conf.d/autologin.conf", "− def456  etc/examen/usuaris.d/examen");
    }

    @Test
    void les_diferencies_es_limiten_per_no_enviar_llistes_enormes() {
        StringBuilder gran = new StringBuilder(LLISTA);
        for (int i = 0; i < 100; i++) gran.append("\nh").append(i).append("  fitxer").append(i);

        List<String> dif = EquipsService.diferencies(LLISTA, gran.toString());

        assertThat(dif).hasSize(EquipsService.MAX_DIFERENCIES + 1);
        assertThat(dif.get(dif.size() - 1)).contains("diferències més");
    }

    @Test
    void sense_referencia_no_es_pot_dir_que_estigui_preparat() {
        EquipsDto d = consulta(null, equip("pc1", LLISTA, ARA.minusMinutes(5), true));

        assertThat(d.equips().get(0).estat()).isEqualTo(Estat.SENSE_REFERENCIA);
        assertThat(d.referencia()).isNull();
        assertThat(d.altres()).isEqualTo(1);
    }

    @Test
    void un_ordinador_que_fa_massa_que_no_informa_es_sense_noticies_encara_que_estigues_be() {
        EquipsDto d = consulta(referencia(LLISTA),
                equip("pc1", LLISTA, ARA.minusMinutes(46), true),
                equip("pc2", LLISTA, ARA.minusMinutes(44), true));

        assertThat(d.equips()).extracting(EquipsDto.Equip::estat).containsExactly(Estat.SENSE_NOTICIES, Estat.PREPARAT);
        assertThat(d.senseNoticies()).isEqualTo(1);
    }

    @Test
    void sense_arribar_a_la_plataforma_no_esta_preparat() {
        EquipsDto d = consulta(referencia(LLISTA),
                equip("pc1", LLISTA, ARA.minusMinutes(5), false),
                equip("pc2", LLISTA, ARA.minusMinutes(5), null));

        assertThat(d.equips()).extracting(EquipsDto.Equip::estat).containsOnly(Estat.SENSE_PLATAFORMA);
    }

    @Test
    void l_alteracio_te_prioritat_sobre_la_plataforma_i_el_silenci_sobre_tot() {
        EquipAula alterat = equip("pc1", "una cosa diferent", ARA.minusMinutes(5), false);
        EquipAula mut = equip("pc2", "una cosa diferent", ARA.minusHours(3), false);

        EquipsDto d = consulta(referencia(LLISTA), alterat, mut);

        assertThat(d.equips()).extracting(EquipsDto.Equip::estat).containsExactly(Estat.ALTERAT, Estat.SENSE_NOTICIES);
    }

    @Test
    void els_avisos_no_treuen_l_estat_de_preparat() {
        EquipAula e = equip("pc1", LLISTA, ARA.minusMinutes(5), true);
        e.setArribaIsard(false);
        e.setDiscLliureMb(500);
        e.setUsuarisDins(2);
        e.setNavegador(" ");

        EquipsDto.Equip r = consulta(referencia(LLISTA), e).equips().get(0);

        assertThat(r.estat()).isEqualTo(Estat.PREPARAT);
        assertThat(r.avisos()).anyMatch(a -> a.contains("Isard")).anyMatch(a -> a.contains("disc"))
                .anyMatch(a -> a.contains("2 sessions")).anyMatch(a -> a.contains("navegador"));
    }

    @Test
    void consultar_una_aula_inexistent_es_un_error() {
        when(aulaRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.equipsDeLAula(UUID.randomUUID(), ARA)).isInstanceOf(NoSuchElementException.class);
    }

    // ── Referència i neteja ───────────────────────────────────────────────────

    @Test
    void fixar_la_referencia_copia_la_llista_de_l_ordinador_i_ho_deixa_a_l_auditoria() {
        EquipAula e = equip("pc19", LLISTA, ARA, true);
        when(equipRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(referenciaRepository.findById(EquipsReferencia.ID)).thenReturn(Optional.empty());

        service.fixaReferencia(e.getId(), admin, ARA);

        ArgumentCaptor<EquipsReferencia> c = ArgumentCaptor.forClass(EquipsReferencia.class);
        verify(referenciaRepository).save(c.capture());
        assertThat(c.getValue().getId()).isEqualTo(EquipsReferencia.ID);
        assertThat(c.getValue().getIntegritatResum()).isEqualTo(e.getIntegritatResum());
        assertThat(c.getValue().getOrigenNom()).isEqualTo("pc19");
        assertThat(c.getValue().getFixadaPer()).isEqualTo(admin.getId());
        verify(auditLog).log(eq(admin.getId()), eq("EQUIPS_REFERENCIA_FIXADA"), contains("pc19"));
    }

    @Test
    void no_es_pot_fixar_com_a_referencia_un_ordinador_sense_llista() {
        EquipAula e = equip("pc19", "", ARA, true);
        when(equipRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.fixaReferencia(e.getId(), admin, ARA)).isInstanceOf(IllegalStateException.class);
        verify(referenciaRepository, never()).save(any());
    }

    @Test
    void esborrar_un_ordinador_el_treu_i_ho_deixa_a_l_auditoria() {
        EquipAula e = equip("pc19", LLISTA, ARA, true);
        when(equipRepository.findById(e.getId())).thenReturn(Optional.of(e));

        service.esborra(e.getId(), admin);

        verify(equipRepository).delete(e);
        verify(auditLog).log(eq(admin.getId()), eq("EQUIP_ESBORRAT"), contains("pc19"));
    }

    // ── Ordinadors que fa temps que no s'encenen ──────────────────────────────

    @Test
    void un_ordinador_que_fa_mes_de_set_dies_que_no_informa_surt_com_a_fa_temps_que_no_s_encen() {
        EquipsDto d = consulta(referencia(LLISTA),
                equip("pc1", LLISTA, ARA.minusDays(8), true),
                equip("pc2", LLISTA, ARA.minusDays(6), true),
                equip("pc3", LLISTA, ARA.minusMinutes(5), true));

        assertThat(d.equips()).extracting(EquipsDto.Equip::faTemps).containsExactly(true, false, false);
        assertThat(d.faTemps()).isEqualTo(1);
        assertThat(d.equips().get(0).diesSenseInformar()).isEqualTo(8);
        // Continua sent «sense notícies»: no se'l pot donar per preparat
        assertThat(d.equips().get(0).estat()).isEqualTo(Estat.SENSE_NOTICIES);
    }

    @Test
    void estar_apagat_unes_hores_no_es_fa_temps_que_no_s_encen_pero_si_es_sense_noticies() {
        EquipsDto d = consulta(referencia(LLISTA), equip("pc1", LLISTA, ARA.minusHours(20), true));

        assertThat(d.equips().get(0).faTemps()).isFalse();
        assertThat(d.equips().get(0).estat()).isEqualTo(Estat.SENSE_NOTICIES);
        assertThat(d.faTemps()).isZero();
    }

}
