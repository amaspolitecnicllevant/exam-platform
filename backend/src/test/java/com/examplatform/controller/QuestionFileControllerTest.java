package com.examplatform.controller;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.QuestionFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Els fitxers de les preguntes els pugen professors: no s'han de poder servir com a HTML o SVG executable. */
class QuestionFileControllerTest {

    QuestionFileService servei;
    MockMvc mvc;
    final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        servei = mock(QuestionFileService.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("a@x.cat").role(Role.STUDENT).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(alumne, null, "ROLE_STUDENT"));
        mvc = MockMvcBuilders.standaloneSetup(new QuestionFileController(servei, mock(AuditLogService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void neteja() {
        SecurityContextHolder.clearContext();
    }

    private MvcResult descarrega(String tipus, String nom) throws Exception {
        when(servei.download(any(), any())).thenReturn(
                new QuestionFileService.Descarrega(new ByteArrayResource("contingut".getBytes()), tipus, nom));
        return mvc.perform(get("/api/files/" + id + "/download")).andExpect(status().isOk()).andReturn();
    }

    @Test
    void un_fitxer_html_es_serveix_com_a_descarrega_generica_i_mai_com_a_html() throws Exception {
        MvcResult r = descarrega("text/html", "pagina.html");

        assertThat(r.getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("Content-Security-Policy")).contains("sandbox");
    }

    @Test
    void un_svg_amb_scripts_tampoc_es_serveix_com_a_imatge() throws Exception {
        assertThat(descarrega("image/svg+xml", "logo.svg").getResponse().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void un_tipus_desconegut_o_amb_text_estrany_es_descarrega_generica() throws Exception {
        assertThat(descarrega("application/x-lo-que-sigui", "a.bin").getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(descarrega("application/javascript", "a.js").getResponse().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void les_imatges_segures_de_mapa_de_bits_conserven_el_tipus_perque_els_enunciats_les_puguin_mostrar() throws Exception {
        assertThat(descarrega("image/png", "a.png").getResponse().getContentType()).isEqualTo("image/png");
        assertThat(descarrega("IMAGE/JPEG", "a.jpg").getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(descarrega("image/webp", "a.webp").getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
