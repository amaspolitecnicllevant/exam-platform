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
        assertThat(controller(null).get().googleActiu()).isFalse();
    }

    @Test
    void amb_google_configurat_s_ofereix() {
        assertThat(controller(mock(ClientRegistrationRepository.class)).get().googleActiu()).isTrue();
    }
}
