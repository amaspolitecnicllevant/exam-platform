package com.examplatform.controller;

import com.examplatform.domain.service.AlertesSeguretatService;
import com.examplatform.dto.AlertaDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertesSeguretatControllerTest {

    @Test
    void nomes_l_administracio_veu_les_alertes() {
        PreAuthorize anotacio = AlertesSeguretatController.class.getAnnotation(PreAuthorize.class);

        assertThat(anotacio).isNotNull();
        assertThat(anotacio.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void torna_les_alertes_del_servei() {
        AlertesSeguretatService servei = mock(AlertesSeguretatService.class);
        AlertaDto a = new AlertaDto("ALTA", "X", "titol", "detall", 1, null);
        when(servei.alertes(any())).thenReturn(List.of(a));

        assertThat(new AlertesSeguretatController(servei).alertes()).containsExactly(a);
    }
}
