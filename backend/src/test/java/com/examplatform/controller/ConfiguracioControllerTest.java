package com.examplatform.controller;

import com.examplatform.domain.model.ConfiguracioSistema;
import com.examplatform.domain.service.ConfiguracioService;
import com.examplatform.domain.service.CopiesSeguretatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConfiguracioControllerTest {

    @SuppressWarnings("unchecked")
    private ConfiguracioController controller(ClientRegistrationRepository oauth) {
        ConfiguracioService service = mock(ConfiguracioService.class);
        when(service.get()).thenReturn(new ConfiguracioSistema());
        ObjectProvider<ClientRegistrationRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(oauth);
        return new ConfiguracioController(service, mock(CopiesSeguretatService.class), provider);
    }

    @Test
    void sense_google_configurat_no_s_ofereix_el_login_amb_google() {
        assertThat(controller(null).get(null).googleActiu()).isFalse();
    }

    @Test
    void amb_google_configurat_s_ofereix() {
        assertThat(controller(mock(ClientRegistrationRepository.class)).get(null).googleActiu()).isTrue();
    }

    private static org.springframework.security.core.Authentication auth(String rol) {
        return new org.springframework.security.authentication.TestingAuthenticationToken("u", null, rol);
    }

    @Test
    void qui_no_ha_entrat_no_veu_els_llindars_antitrampes() {
        var c = controller(null).get(null);

        assertThat(c.nomCentre()).isNotNull();
        assertThat(c.focusLossThreshold()).isZero();
        assertThat(c.copiesLlindar()).isZero();
        assertThat(c.copiesLlindarApunts()).isZero();
        assertThat(c.gracePeriodSeconds()).isZero();
        assertThat(c.dominisOauth()).isEmpty();
    }

    @Test
    void un_alumne_tampoc_veu_els_llindars_ni_el_curs() {
        var c = controller(null).get(auth("ROLE_STUDENT"));

        assertThat(c.copiesLlindar()).isZero();
        assertThat(c.focusLossThreshold()).isZero();
        assertThat(c.cursActiu()).isEmpty();
    }

    @Test
    void el_professorat_i_l_administracio_veuen_la_configuracio_completa() {
        var professor = controller(null).get(auth("ROLE_PROFESSOR"));
        var admin = controller(null).get(auth("ROLE_ADMIN"));

        assertThat(professor.copiesLlindar()).isPositive();
        assertThat(professor.focusLossThreshold()).isPositive();
        assertThat(admin.copiesLlindar()).isPositive();
    }

    @Test
    void la_configuracio_publica_conserva_el_que_cal_per_entrar_i_per_pujar_fitxers() {
        var c = controller(null).get(null);

        assertThat(c.colorMarca()).isNotNull();
        assertThat(c.pujadaFitxersActiva()).isTrue();
    }
}
