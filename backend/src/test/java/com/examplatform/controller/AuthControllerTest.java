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
        when(jwt.generateToken(alumne)).thenReturn("nou-token");
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class));

        var r = c.refresh(alumne);

        assertThat(r.token()).isEqualTo("nou-token");
        assertThat(r.userId()).isEqualTo(alumne.getId());
        assertThat(r.role()).isEqualTo(Role.STUDENT);
    }
}
