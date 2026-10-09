package com.examplatform.controller;

import com.examplatform.config.GlobalExceptionHandler;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.FitxerRespostaService;
import com.examplatform.dto.AnswerDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contracte HTTP de la pujada: multipart, capçaleres de descàrrega i traducció d'errors. */
class RespostaFitxerControllerTest {

    final UUID sessio = UUID.randomUUID();
    final UUID pregunta = UUID.randomUUID();
    final String url = "/api/sessions/" + sessio + "/questions/" + pregunta + "/file";

    FitxerRespostaService service;
    MockMvc mvc;
    User alumne;

    @BeforeEach
    void setUp() {
        service = mock(FitxerRespostaService.class);
        alumne = User.builder().id(UUID.randomUUID()).name("Alumne").email("a@x.cat").role(Role.STUDENT).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(alumne, null, "ROLE_STUDENT"));
        mvc = MockMvcBuilders.standaloneSetup(new RespostaFitxerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void neteja() {
        SecurityContextHolder.clearContext();
    }

    private AnswerDto resposta(String nom, long mida) {
        return new AnswerDto(UUID.randomUUID(), pregunta, nom, null, null, null, null, null, nom, mida, null);
    }

    private MockMultipartFile fitxer() {
        return new MockMultipartFile("file", "Treball.docx", "application/octet-stream", new byte[]{'P', 'K', 3, 4});
    }

    // ── pujada ────────────────────────────────────────────────────────────────

    @Test
    void puja_envia_el_fitxer_al_servei_amb_l_alumne_i_retorna_el_nom_i_la_mida() throws Exception {
        when(service.puja(eq(sessio), eq(pregunta), any(), eq(alumne), anyString())).thenReturn(resposta("Treball.docx", 4));

        mvc.perform(multipart(url).file(fitxer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitxerNom").value("Treball.docx"))
                .andExpect(jsonPath("$.fitxerMida").value(4));

        verify(service).puja(eq(sessio), eq(pregunta),
                argThat(f -> "Treball.docx".equals(f.getOriginalFilename()) && f.getSize() == 4), eq(alumne), anyString());
    }

    @Test
    void puja_sense_fitxer_es_un_error_de_l_usuari_i_no_un_500() throws Exception {
        mvc.perform(multipart(url))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(service);
    }

    @Test
    void errors_del_servei_es_tradueixen_a_codis_http_amb_missatge() throws Exception {
        when(service.puja(any(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("Aquesta pregunta només admet fitxers .docx"))
                .thenThrow(new IllegalStateException("La sessió ja ha estat enviada"))
                .thenThrow(new AccessDeniedException("No tens accés"))
                .thenThrow(new NoSuchElementException("Pregunta no trobada"));

        mvc.perform(multipart(url).file(fitxer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Aquesta pregunta només admet fitxers .docx"));
        mvc.perform(multipart(url).file(fitxer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La sessió ja ha estat enviada"));
        mvc.perform(multipart(url).file(fitxer())).andExpect(status().isForbidden());
        mvc.perform(multipart(url).file(fitxer())).andExpect(status().isNotFound());
    }

    // ── esborrat ──────────────────────────────────────────────────────────────

    @Test
    void esborra_retorna_la_resposta_sense_fitxer() throws Exception {
        when(service.esborra(eq(sessio), eq(pregunta), eq(alumne), anyString())).thenReturn(resposta(null, 0));

        mvc.perform(delete(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitxerNom").doesNotExist());
    }

    // ── descàrrega ────────────────────────────────────────────────────────────

    private void descarrega(String nom, byte[] contingut) {
        when(service.descarrega(sessio, pregunta, alumne))
                .thenReturn(new FitxerRespostaService.Descarrega(new ByteArrayResource(contingut), nom));
    }

    @Test
    void descarrega_sempre_com_a_adjunt_binari_que_el_navegador_no_interpreta() throws Exception {
        byte[] contingut = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        descarrega("Treball.docx", contingut);

        var res = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(res.getContentType()).isEqualTo("application/octet-stream");
        assertThat(res.getHeader("Content-Disposition")).startsWith("attachment").contains("Treball.docx");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("Cache-Control")).contains("no-store");
        assertThat(res.getContentAsByteArray()).isEqualTo(contingut);
    }

    @Test
    void descarrega_amb_un_nom_amb_accents_cometes_i_salts_de_linia_no_trenca_la_capcalera() throws Exception {
        descarrega("Informe àçè \"final\"\r\nX-Injectat: 1.docx", new byte[]{1});

        var res = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(res.getHeader("X-Injectat")).isNull();
        String cd = res.getHeader("Content-Disposition");
        assertThat(cd).startsWith("attachment");
        assertThat(cd).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void descarrega_sense_permis_o_sense_fitxer_dona_403_o_404() throws Exception {
        when(service.descarrega(sessio, pregunta, alumne))
                .thenThrow(new AccessDeniedException("No tens accés als fitxers d'aquesta sessió"))
                .thenThrow(new NoSuchElementException("No hi ha cap fitxer pujat en aquesta pregunta"));

        mvc.perform(get(url)).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
