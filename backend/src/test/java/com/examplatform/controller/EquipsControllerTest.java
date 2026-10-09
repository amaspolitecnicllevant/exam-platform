package com.examplatform.controller;

import com.examplatform.config.GlobalExceptionHandler;
import com.examplatform.config.InformeEquipsRateLimiter;
import com.examplatform.domain.service.EquipsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EquipsControllerTest {

    EquipsService servei;
    InformeEquipsRateLimiter limitador;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        servei = mock(EquipsService.class);
        limitador = mock(InformeEquipsRateLimiter.class);
        mvc = MockMvcBuilders.standaloneSetup(new EquipsController(servei, limitador))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder informe() {
        return post("/api/equips/informe").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(r -> { r.setRemoteAddr("10.100.94.19"); return r; })
                .header("X-Equip-Token", "el-testimoni");
    }

    @Test
    void un_informe_correcte_respon_204_i_passa_les_dades_al_servei_amb_la_ip_d_origen() throws Exception {
        mvc.perform(informe().param("nom", "pc19").param("integritat", "a  x\nb  y").param("arribaPlataforma", "true")
                        .param("arribaIsard", "false").param("navegador", "Firefox 155").param("discLliureMb", "20000")
                        .param("uptimeSegons", "3600").param("usuarisDins", "0"))
                .andExpect(status().isNoContent());

        verify(limitador).consumeix("10.100.94.19");
        ArgumentCaptor<EquipsService.Informe> c = ArgumentCaptor.forClass(EquipsService.Informe.class);
        verify(servei).registraInforme(eq("el-testimoni"), eq("10.100.94.19"), c.capture(), any());
        assertThat(c.getValue().nom()).isEqualTo("pc19");
        assertThat(c.getValue().integritat()).isEqualTo("a  x\nb  y");
        assertThat(c.getValue().arribaPlataforma()).isTrue();
        assertThat(c.getValue().arribaIsard()).isFalse();
        assertThat(c.getValue().discLliureMb()).isEqualTo(20000);
        assertThat(c.getValue().uptimeSegons()).isEqualTo(3600L);
    }

    @Test
    void nomes_el_nom_es_obligatori() throws Exception {
        mvc.perform(informe().param("nom", "pc19")).andExpect(status().isNoContent());
        mvc.perform(informe()).andExpect(status().isBadRequest());
    }

    @Test
    void no_accepta_json_nomes_formulari() throws Exception {
        mvc.perform(post("/api/equips/informe").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"pc19\"}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(servei);
    }

    @Test
    void si_s_ha_passat_el_limit_d_informes_no_arriba_al_servei() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(limitador).consumeix(any());

        mvc.perform(informe().param("nom", "pc19")).andExpect(status().isTooManyRequests());
        verifyNoInteractions(servei);
    }

    @Test
    void els_errors_de_seguretat_del_servei_arriben_tal_qual() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Testimoni no vàlid"))
                .when(servei).registraInforme(any(), any(), any(), any());

        mvc.perform(informe().param("nom", "pc19")).andExpect(status().isUnauthorized());
    }

    // ── Autorització: el controlador standalone no aplica @PreAuthorize, així que es comprova que hi sigui ──

    private static String preAuthorize(String metode) {
        return Arrays.stream(EquipsController.class.getDeclaredMethods()).filter(m -> m.getName().equals(metode))
                .findFirst().map(m -> m.getAnnotation(PreAuthorize.class)).map(PreAuthorize::value).orElse(null);
    }

    @Test
    void consultar_l_estat_de_l_aula_es_per_a_professors_i_administradors() {
        assertThat(preAuthorize("equips")).isEqualTo("hasAnyRole('PROFESSOR','ADMIN')");
    }

    @Test
    void fixar_la_referencia_i_esborrar_ordinadors_nomes_ho_fa_l_administrador() {
        assertThat(preAuthorize("fixaReferencia")).isEqualTo("hasRole('ADMIN')");
        assertThat(preAuthorize("esborra")).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void l_informe_no_demana_sessio_perque_l_envien_els_ordinadors() {
        assertThat(preAuthorize("informe")).isNull();
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
