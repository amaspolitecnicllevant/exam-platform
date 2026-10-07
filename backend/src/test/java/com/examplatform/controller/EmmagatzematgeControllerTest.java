package com.examplatform.controller;

import com.examplatform.config.GlobalExceptionHandler;
import com.examplatform.domain.service.emmagatzematge.Agrupacio;
import com.examplatform.domain.service.emmagatzematge.EmmagatzematgeService;
import com.examplatform.dto.EmmagatzematgeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmmagatzematgeControllerTest {

    EmmagatzematgeService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmmagatzematgeService.class);
        var fila = new EmmagatzematgeDto.Fila("t", "Total", null, 3, 2, 150, 4, 1500, 1650);
        when(service.auditoria(any())).thenReturn(new EmmagatzematgeDto(
                new EmmagatzematgeDto.Taula("professor", List.of(new EmmagatzematgeDto.Fila("k", "Anna", null, 3, 2, 150, 4, 1500, 1650)), fila),
                new EmmagatzematgeDto.Disc(150, 1500, 5_000_000L, 10_000_000L)));
        mvc = MockMvcBuilders.standaloneSetup(new EmmagatzematgeController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void per_defecte_agrupa_per_professor_i_retorna_la_taula_i_el_disc() throws Exception {
        mvc.perform(get("/api/admin/emmagatzematge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taula.agrupa").value("professor"))
                .andExpect(jsonPath("$.taula.files[0].nom").value("Anna"))
                .andExpect(jsonPath("$.taula.files[0].total").value(1650))
                .andExpect(jsonPath("$.taula.total.examens").value(3))
                .andExpect(jsonPath("$.disc.midaLliuramentsDisc").value(1500))
                .andExpect(jsonPath("$.disc.espaiLliure").value(5_000_000));
        verify(service).auditoria(Agrupacio.PROFESSOR);
    }

    @Test
    void accepta_cada_agrupacio() throws Exception {
        for (String a : List.of("departament", "cicle", "modul", "examen")) {
            mvc.perform(get("/api/admin/emmagatzematge").param("agrupa", a)).andExpect(status().isOk());
        }
        verify(service).auditoria(Agrupacio.DEPARTAMENT);
        verify(service).auditoria(Agrupacio.CICLE);
        verify(service).auditoria(Agrupacio.MODUL);
        verify(service).auditoria(Agrupacio.EXAMEN);
    }

    @Test
    void una_agrupacio_desconeguda_es_un_error_de_l_usuari_amb_les_opcions() throws Exception {
        mvc.perform(get("/api/admin/emmagatzematge").param("agrupa", "alumne"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("professor, departament, cicle, modul, examen")));
        verifyNoInteractions(service);
    }
}
