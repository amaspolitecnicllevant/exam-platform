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
        assertThatThrownBy(() -> new JwtUtil(secret, 3600_000, 43_200_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void no_arrenca_amb_un_secret_massa_curt() {
        assertThatThrownBy(() -> new JwtUtil("curt", 3600_000, 43_200_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("massa curt");
    }

    @Test
    void un_token_signat_amb_un_altre_secret_no_es_valid() {
        var usuari = User.withUsername("prof@test.cat").password("x").authorities(List.of()).build();
        JwtUtil meu = new JwtUtil(SECRET, 3600_000, 43_200_000);
        JwtUtil altre = new JwtUtil(Base64.getEncoder().encodeToString("un-altre-secret-de-32-bytes-o-mes!!".getBytes()), 3600_000, 43_200_000);

        String falsificat = altre.generateToken(usuari);

        assertThat(meu.isValid(meu.generateToken(usuari), usuari)).isTrue();
        assertThatThrownBy(() -> meu.extractEmail(falsificat))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    // ── Sessions: vida màxima, revocació i usuari habilitat ───────────────────

    private static com.examplatform.domain.model.User dom(int versio, boolean habilitat) {
        return com.examplatform.domain.model.User.builder().id(java.util.UUID.randomUUID()).name("Prof")
                .email("prof@test.cat").role(com.examplatform.domain.model.Role.PROFESSOR)
                .tokenVersion(versio).enabled(habilitat).build();
    }

    @Test
    void un_token_d_un_usuari_inhabilitat_no_es_valid_ni_es_pot_renovar_amb_ell() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 43_200_000);
        String token = jwt.generateToken(dom(0, true));

        assertThat(jwt.isValid(token, dom(0, true))).isTrue();
        var inhabilitat = dom(0, false);
        assertThat(jwt.isValid(token, inhabilitat)).isFalse();
    }

    @Test
    void pujar_la_versio_de_token_de_l_usuari_revoca_tots_els_tokens_anteriors() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 43_200_000);
        var abans = dom(3, true);
        String token = jwt.generateToken(abans);
        var despres = dom(4, true);
        despres.setEmail(abans.getEmail());

        assertThat(jwt.isValid(token, abans)).isTrue();
        assertThat(jwt.isValid(token, despres)).isFalse();
        assertThat(jwt.isValid(jwt.generateToken(despres), despres)).isTrue();
    }

    @Test
    void un_token_de_l_usuari_de_la_versio_correcta_pero_d_un_altre_correu_no_es_valid() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 43_200_000);
        String token = jwt.generateToken(dom(0, true));
        var altre = dom(0, true);
        altre.setEmail("altre@test.cat");

        assertThat(jwt.isValid(token, altre)).isFalse();
    }

    @Test
    void una_sessio_que_ha_superat_la_vida_maxima_no_es_valida_ni_es_pot_renovar() throws Exception {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 50);
        var usuari = dom(0, true);
        String token = jwt.generateToken(usuari);
        assertThat(jwt.isValid(token, usuari)).isTrue();

        Thread.sleep(120);

        assertThat(jwt.isValid(token, usuari)).isFalse();
        assertThatThrownBy(() -> jwt.renova(token, usuari)).isInstanceOf(io.jsonwebtoken.JwtException.class)
                .hasMessageContaining("vida màxima");
    }

    @Test
    void renovar_un_token_no_allarga_la_sessio_mes_enlla_de_la_vida_maxima() throws Exception {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 300);
        var usuari = dom(0, true);
        String inicial = jwt.generateToken(usuari);

        Thread.sleep(180);
        String renovat = jwt.renova(inicial, usuari);       // encara dins de la vida màxima
        assertThat(jwt.isValid(renovat, usuari)).isTrue();

        Thread.sleep(180);                                  // 360 ms des del login: ja la supera encara que el token sigui «nou»
        assertThat(jwt.isValid(renovat, usuari)).isFalse();
        assertThatThrownBy(() -> jwt.renova(renovat, usuari)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void un_token_emes_abans_d_aquesta_versio_sense_les_claus_noves_continua_valid_mentre_no_es_revoqui() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600_000, 43_200_000);
        javax.crypto.SecretKey clau = io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(SECRET));
        String antic = io.jsonwebtoken.Jwts.builder().subject("prof@test.cat")
                .issuedAt(new java.util.Date()).expiration(new java.util.Date(System.currentTimeMillis() + 3600_000))
                .signWith(clau).compact();

        assertThat(jwt.isValid(antic, dom(0, true))).isTrue();
        assertThat(jwt.isValid(antic, dom(1, true))).isFalse();
    }

    @Test
    void el_temps_maxim_de_sessio_ha_de_ser_positiu() {
        assertThatThrownBy(() -> new JwtUtil(SECRET, 3600_000, 0)).isInstanceOf(IllegalStateException.class);
    }
}
