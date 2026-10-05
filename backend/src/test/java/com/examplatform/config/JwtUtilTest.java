package com.examplatform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.User;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]).replace('A', 'k');

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "canviam-aquest-secret-en-produccio-min32chars!!"})
    void no_arrenca_sense_secret_propi(String secret) {
        assertThatThrownBy(() -> new JwtUtil(secret, 3600_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void no_arrenca_amb_un_secret_massa_curt() {
        assertThatThrownBy(() -> new JwtUtil("curt", 3600_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("massa curt");
    }

    @Test
    void un_token_signat_amb_un_altre_secret_no_es_valid() {
        var usuari = User.withUsername("prof@test.cat").password("x").authorities(List.of()).build();
        JwtUtil meu = new JwtUtil(SECRET, 3600_000);
        JwtUtil altre = new JwtUtil(Base64.getEncoder().encodeToString("un-altre-secret-de-32-bytes-o-mes!!".getBytes()), 3600_000);

        String falsificat = altre.generateToken(usuari);

        assertThat(meu.isValid(meu.generateToken(usuari), usuari)).isTrue();
        assertThatThrownBy(() -> meu.extractEmail(falsificat))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
