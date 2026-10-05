package com.examplatform.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CidrUtilTest {

    // ── Casos normals /24 ─────────────────────────────────────────────────────

    @Test
    void ip_dins_del_rang_24_retorna_true() {
        assertThat(CidrUtil.isInCidr("10.0.1.50", "10.0.1.0/24")).isTrue();
    }

    @Test
    void ip_primera_del_rang_24_retorna_true() {
        assertThat(CidrUtil.isInCidr("10.0.1.0", "10.0.1.0/24")).isTrue();
    }

    @Test
    void ip_darrera_del_rang_24_retorna_true() {
        assertThat(CidrUtil.isInCidr("10.0.1.255", "10.0.1.0/24")).isTrue();
    }

    @Test
    void ip_fora_del_rang_24_retorna_false() {
        assertThat(CidrUtil.isInCidr("10.0.2.1", "10.0.1.0/24")).isFalse();
    }

    // ── /32 host únic ─────────────────────────────────────────────────────────

    @Test
    void rang_32_accepta_exactament_la_ip_indicada() {
        assertThat(CidrUtil.isInCidr("192.168.1.100", "192.168.1.100/32")).isTrue();
    }

    @Test
    void rang_32_rebutja_qualsevol_altra_ip() {
        assertThat(CidrUtil.isInCidr("192.168.1.101", "192.168.1.100/32")).isFalse();
    }

    // ── /0 accepta tot ────────────────────────────────────────────────────────

    @Test
    void rang_0_accepta_qualsevol_ip() {
        assertThat(CidrUtil.isInCidr("1.2.3.4",       "0.0.0.0/0")).isTrue();
        assertThat(CidrUtil.isInCidr("255.255.255.255","0.0.0.0/0")).isTrue();
    }

    // ── /16 i /8 ──────────────────────────────────────────────────────────────

    @Test
    void rang_16_accepta_ip_del_mateix_bloc() {
        assertThat(CidrUtil.isInCidr("172.16.200.1", "172.16.0.0/16")).isTrue();
    }

    @Test
    void rang_16_rebutja_ip_fora_del_bloc() {
        assertThat(CidrUtil.isInCidr("172.17.0.1", "172.16.0.0/16")).isFalse();
    }

    @Test
    void rang_8_accepta_ip_del_mateix_octet() {
        assertThat(CidrUtil.isInCidr("10.200.100.50", "10.0.0.0/8")).isTrue();
    }

    // ── Prefixos no alineats a byte (/20, /25) ────────────────────────────────

    @Test
    void rang_20_accepta_ip_del_bloc() {
        // 192.168.32.0/20 cobreix 192.168.32.0 – 192.168.47.255
        assertThat(CidrUtil.isInCidr("192.168.47.1", "192.168.32.0/20")).isTrue();
    }

    @Test
    void rang_20_rebutja_ip_fora_del_bloc() {
        assertThat(CidrUtil.isInCidr("192.168.48.1", "192.168.32.0/20")).isFalse();
    }

    @Test
    void rang_25_accepta_primera_meitat() {
        // 10.0.0.0/25 cobreix 10.0.0.0 – 10.0.0.127
        assertThat(CidrUtil.isInCidr("10.0.0.127", "10.0.0.0/25")).isTrue();
    }

    @Test
    void rang_25_rebutja_segona_meitat() {
        assertThat(CidrUtil.isInCidr("10.0.0.128", "10.0.0.0/25")).isFalse();
    }

    // ── IP amb port (via proxy invers) ────────────────────────────────────────

    @Test
    void ip_amb_port_es_parseja_correctament() {
        assertThat(CidrUtil.isInCidr("10.0.1.50:54321", "10.0.1.0/24")).isTrue();
    }

    // ── Entrades invàlides retornen false (mai excepció) ──────────────────────

    @ParameterizedTest
    @CsvSource({
        "no-una-ip,       10.0.1.0/24",
        "10.0.1.1,        10.0.1.0/99",   // prefix fora de rang
        "10.0.1.1,        10.0.1.0",       // sense prefix
        "10.0.1.1,        ''",              // cidr buit
        "'',              10.0.1.0/24",     // ip buida
        "10.0.1.1,        10.0.1.0/abc",   // prefix no numèric
    })
    void entrades_invalides_retornen_false(String ip, String cidr) {
        assertThat(CidrUtil.isInCidr(ip, cidr)).isFalse();
    }
}
