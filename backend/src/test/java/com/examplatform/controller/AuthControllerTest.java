package com.examplatform.controller;

import com.examplatform.config.JwtUtil;
import com.examplatform.config.LoginRateLimiter;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Test
    void refresh_dona_un_token_nou_al_mateix_usuari() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("el-token-actual", alumne)).thenReturn("nou-token");
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class));

        var peticio = new org.springframework.mock.web.MockHttpServletRequest();
        peticio.addHeader("Authorization", "Bearer el-token-actual");

        var r = c.refresh(peticio, alumne);

        assertThat(r.token()).isEqualTo("nou-token");
        assertThat(r.userId()).isEqualTo(alumne.getId());
        assertThat(r.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void refresh_d_una_sessio_que_ha_arribat_a_la_vida_maxima_respon_401() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("vell", alumne)).thenThrow(new io.jsonwebtoken.JwtException("vida màxima"));
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class));
        var peticio = new org.springframework.mock.web.MockHttpServletRequest();
        peticio.addHeader("Authorization", "Bearer vell");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.refresh(peticio, alumne))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void refresh_sense_capcalera_d_autoritzacio_no_dona_cap_token() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("", alumne)).thenThrow(new IllegalArgumentException("buit"));
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.refresh(new org.springframework.mock.web.MockHttpServletRequest(), alumne))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
