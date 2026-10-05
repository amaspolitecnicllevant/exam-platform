package com.examplatform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Els errors del client han de ser 4xx amb un missatge, no 500. */
class GlobalExceptionHandlerTest {

    record Peticio(@NotBlank String nom) {}

    @RestController
    static class Prova {
        @PostMapping("/json") String json(@Valid @RequestBody Peticio p) { return p.nom(); }
        @GetMapping("/uuid/{id}") String uuid(@PathVariable UUID id) { return id.toString(); }
        @GetMapping("/param") String param(@RequestParam int n) { return "" + n; }
        @DeleteMapping("/bd") void bd() { throw new DataIntegrityViolationException("fk"); }
        @GetMapping("/intern") void intern() { throw new NullPointerException("secret intern"); }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Prova())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void json_mal_format_es_400() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{malformat"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Petició mal formada"));
    }

    @Test
    void validacio_fallida_es_400_amb_el_camp() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("nom")));
    }

    @Test
    void identificador_invalid_o_parametre_absent_es_400() throws Exception {
        mvc.perform(get("/uuid/no-es-un-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/param")).andExpect(status().isBadRequest());
        mvc.perform(get("/param").param("n", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void metode_o_tipus_de_contingut_incorrectes() throws Exception {
        mvc.perform(put("/json")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void restriccio_de_la_bd_es_409() throws Exception {
        mvc.perform(delete("/bd")).andExpect(status().isConflict());
    }

    @Test
    void error_intern_no_exposa_detalls() throws Exception {
        mvc.perform(get("/intern"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error intern del servidor"));
    }
}
