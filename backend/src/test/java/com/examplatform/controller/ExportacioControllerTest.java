package com.examplatform.controller;

import com.examplatform.config.GlobalExceptionHandler;
import com.examplatform.domain.model.*;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.exportacio.*;
import com.examplatform.domain.service.exportacio.FixtureExamen;
import com.examplatform.dto.ExamStatsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contracte HTTP de les exportacions: tipus, capçaleres, anonimat per defecte i registre d'activitat. */
class ExportacioControllerTest {

    ExportacioService service;
    AuditLogService audit;
    MockMvc mvc;
    User prof;
    FixtureExamen f;
    UUID examId;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(ExportacioService.class);
        audit = mock(AuditLogService.class);
        prof = User.builder().id(UUID.randomUUID()).name("Prof").email("p@x.cat").role(Role.PROFESSOR).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(prof, null, "ROLE_PROFESSOR"));
        mvc = MockMvcBuilders.standaloneSetup(new ExportacioController(service, audit))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        f = new FixtureExamen();
        f.exam.setTitle("Parcial UT1: Xarxes / «Part 2»");
        Question p = f.pregunta(QuestionType.SHORT, "10", null);
        f.resposta(f.entregat("Maria Roca", "maria@centre.cat"), p, "La meva resposta", "7");
        examId = f.exam.getId();
        when(service.dades(eq(examId), any())).thenReturn(f.dades());
    }

    @AfterEach
    void neteja() {
        SecurityContextHolder.clearContext();
    }

    private String url(String final_) {
        return "/api/export/exam/" + examId + "/" + final_;
    }

    private static String cos(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ── CSV ───────────────────────────────────────────────────────────────────

    @Test
    void notes_csv_es_un_adjunt_utf8_no_interpretable_i_queda_auditat() throws Exception {
        MvcResult r = mvc.perform(get(url("notes.csv"))).andExpect(status().isOk()).andReturn();

        assertThat(r.getResponse().getContentType()).startsWith("text/csv");
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment").contains("notes.csv");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(cos(r)).startsWith("﻿alumne;email;estat;nota_sobre_10").contains("Maria Roca");
        verify(audit).log(eq(prof.getId()), eq("EXAM_EXPORTED"), contains("notes"));
    }

    @Test
    void el_nom_del_fitxer_surt_del_titol_sense_caracters_perillosos() throws Exception {
        MvcResult r = mvc.perform(get(url("notes.csv"))).andReturn();

        String cd = r.getResponse().getHeader("Content-Disposition");
        assertThat(cd).doesNotContain("/").doesNotContain("\r").doesNotContain("\n").contains("Parcial_UT1");
    }

    @Test
    void notes_ra_i_clau_alumnes_son_csv() throws Exception {
        assertThat(mvc.perform(get(url("notes-ra.csv"))).andExpect(status().isOk()).andReturn().getResponse().getContentType())
                .startsWith("text/csv");
        MvcResult clau = mvc.perform(get(url("clau-alumnes.csv"))).andExpect(status().isOk()).andReturn();
        assertThat(cos(clau)).contains("codi;alumne;email;estat").contains("Maria Roca");
        verify(audit).log(eq(prof.getId()), eq("EXAM_EXPORTED"), contains("clau"));
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    @Test
    void les_respostes_en_markdown_son_anonimes_per_defecte() throws Exception {
        MvcResult r = mvc.perform(get(url("respostes.md"))).andExpect(status().isOk()).andReturn();

        assertThat(r.getResponse().getContentType()).startsWith("text/markdown");
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains("respostes-anonimes.md");
        assertThat(cos(r)).doesNotContain("Maria").doesNotContain("centre.cat").contains("alumnes anonimitzats");
        verify(audit).log(eq(prof.getId()), eq("EXAM_EXPORTED"), contains("anònim"));
    }

    @Test
    void amb_anonim_false_porta_noms_i_ho_deixa_clarament_al_registre() throws Exception {
        MvcResult r = mvc.perform(get(url("respostes.md")).param("anonim", "false")).andExpect(status().isOk()).andReturn();

        assertThat(cos(r)).contains("Maria Roca <maria@centre.cat>");
        verify(audit).log(eq(prof.getId()), eq("EXAM_EXPORTED"), contains("AMB NOMS"));
    }

    @Test
    void model_false_treu_la_resposta_model() throws Exception {
        f.exam.getQuestions().get(0).setModelResposta("SOLUCIÓ SECRETA");

        assertThat(cos(mvc.perform(get(url("respostes.md"))).andReturn())).contains("SOLUCIÓ SECRETA");
        assertThat(cos(mvc.perform(get(url("respostes.md")).param("model", "false")).andReturn())).doesNotContain("SOLUCIÓ SECRETA");
    }

    @Test
    void l_informe_es_markdown() throws Exception {
        when(service.estadistiques(eq(examId), any())).thenReturn(new ExamStatsDto(1, 1, 0, new BigDecimal("7"), new BigDecimal("7"),
                new BigDecimal("7"), new BigDecimal("7"), new BigDecimal("100"), List.of(0, 0, 0, 0, 0, 0, 0, 1, 0, 0), List.of()));

        MvcResult r = mvc.perform(get(url("informe.md"))).andExpect(status().isOk()).andReturn();

        assertThat(r.getResponse().getContentType()).startsWith("text/markdown");
        assertThat(cos(r)).startsWith("# Informe de l'examen:");
    }

    // ── Excel ─────────────────────────────────────────────────────────────────

    @Test
    void l_excel_te_el_tipus_xlsx_i_es_un_zip_valid() throws Exception {
        MvcResult r = mvc.perform(get(url("examen.xlsx"))).andExpect(status().isOk()).andReturn();

        assertThat(r.getResponse().getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains(".xlsx");
        byte[] bytes = r.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(new XlsxLectorPublic(bytes).nomsDeFull()).containsExactly("Notes", "Notes per RA", "Respostes");
    }

    // ── ZIP en flux ───────────────────────────────────────────────────────────

    @Test
    void el_zip_s_envia_en_flux_com_a_adjunt_i_conte_els_fitxers() throws Exception {
        Path origen = Files.createTempFile("lliurament", ".bin");
        Files.writeString(origen, "contingut del lliurament");
        var pla = new ExportacioFitxers.Pla(List.of(new ExportacioFitxers.Entrada("Maria (maria)/P1-treball.docx", origen)),
                List.of(), "Parcial", LocalDate.of(2026, 10, 6));
        when(service.plaFitxers(eq(examId), any())).thenReturn(pla);

        MvcResult inicial = mvc.perform(get(url("fitxers.zip"))).andExpect(request().asyncStarted()).andReturn();
        MvcResult r = mvc.perform(asyncDispatch(inicial)).andExpect(status().isOk()).andReturn();

        assertThat(r.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment").contains("lliuraments.zip");
        Map<String, String> entrades = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) entrades.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(entrades.keySet()).containsExactly("LLEGEIX-ME.txt", "Maria (maria)/P1-treball.docx");
        assertThat(entrades.get("Maria (maria)/P1-treball.docx")).isEqualTo("contingut del lliurament");
        verify(audit).log(eq(prof.getId()), eq("EXAM_EXPORTED"), contains("ZIP"));
        Files.deleteIfExists(origen);
    }

    // ── permisos i errors ─────────────────────────────────────────────────────

    @Test
    void qui_no_pot_gestionar_l_examen_no_exporta_res_ni_queda_cap_registre() throws Exception {
        when(service.dades(eq(examId), any())).thenThrow(new AccessDeniedException("No ets el propietari"));

        for (String ruta : List.of("notes.csv", "notes-ra.csv", "respostes.md", "clau-alumnes.csv", "examen.xlsx")) {
            mvc.perform(get(url(ruta))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(audit);
    }

    @Test
    void un_examen_inexistent_es_404() throws Exception {
        when(service.dades(eq(examId), any())).thenThrow(new NoSuchElementException("Examen no trobat"));

        mvc.perform(get(url("notes.csv"))).andExpect(status().isNotFound());
    }

    /** Lector mínim de noms de full, per no dependre de la classe privada del paquet de domini. */
    static final class XlsxLectorPublic {
        private final String workbook;

        XlsxLectorPublic(byte[] xlsx) throws Exception {
            String wb = "";
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    if (e.getName().equals("xl/workbook.xml")) wb = new String(z.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            workbook = wb;
        }

        List<String> nomsDeFull() {
            List<String> noms = new ArrayList<>();
            var m = java.util.regex.Pattern.compile("<sheet name=\"([^\"]+)\"").matcher(workbook);
            while (m.find()) noms.add(m.group(1));
            return noms;
        }
    }
}
