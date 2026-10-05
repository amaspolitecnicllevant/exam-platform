package com.examplatform.domain.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClausCorreccioTest {

    private static ClausCorreccio claus(String raw, String punts) {
        return ClausCorreccio.parse(raw, new BigDecimal(punts));
    }

    // ── Format del bloc :::clau ──────────────────────────────────────────────

    @Nested
    class Parse {

        @Test
        void sense_pesos_reparteix_els_punts_a_parts_iguals() {
            ClausCorreccio c = claus("DHCP\nDNS", "2");

            assertThat(c.claus()).extracting(ClausCorreccio.Clau::pes)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("1"), new BigDecimal("1"));
        }

        @Test
        void repartiment_no_exacte_compensa_a_l_ultim_i_suma_els_punts() {
            ClausCorreccio c = claus("a\nb\nc", "1");

            assertThat(c.claus()).extracting(ClausCorreccio.Clau::pes)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("0.33"), new BigDecimal("0.33"), new BigDecimal("0.34"));
        }

        @Test
        void amb_pesos_els_respecta() {
            ClausCorreccio c = claus("DHCP | 1.5\nIP | 0,5", "2");

            assertThat(c.claus()).extracting(ClausCorreccio.Clau::pes)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("1.5"), new BigDecimal("0.5"));
        }

        @Test
        void alternatives_separades_per_comes() {
            ClausCorreccio c = claus("adreça IP, IP ,  direcció IP", "1");

            assertThat(c.claus()).singleElement().satisfies(clau -> {
                assertThat(clau.alternatives()).containsExactly("adreça IP", "IP", "direcció IP");
                assertThat(clau.nom()).isEqualTo("adreça IP");
            });
        }

        @Test
        void ignora_linies_en_blanc() {
            assertThat(claus("\n  DHCP  \n\n   \nDNS\n", "2").claus()).hasSize(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\n\n"})
        void bloc_buit_falla(String raw) {
            assertThatThrownBy(() -> claus(raw, "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("buit");
        }

        @Test
        void null_falla() {
            assertThatThrownBy(() -> ClausCorreccio.parse(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void pesos_barrejats_falla() {
            assertThatThrownBy(() -> claus("DHCP | 1\nDNS", "2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tots");
        }

        @Test
        void pesos_que_no_sumen_els_punts_falla() {
            assertThatThrownBy(() -> claus("DHCP | 1\nDNS | 0.5", "2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1,5")
                    .hasMessageContaining("2");
        }

        @ParameterizedTest
        @ValueSource(strings = {"DHCP | 0", "DHCP | -1"})
        void pes_no_positiu_falla(String raw) {
            assertThatThrownBy(() -> claus(raw, "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positiu");
        }

        @Test
        void pes_no_numeric_falla() {
            assertThatThrownBy(() -> claus("DHCP | molt", "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("numèric");
        }

        @Test
        void massa_separadors_falla() {
            assertThatThrownBy(() -> claus("DHCP | 1 | 2", "1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"| 1", " , , | 1", "... | 1"})
        void concepte_buit_falla(String raw) {
            assertThatThrownBy(() -> claus(raw, "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("buit");
        }
    }

    // ── Proposta de nota ─────────────────────────────────────────────────────

    @Nested
    class Avalua {

        @Test
        void tots_els_conceptes_presents_dona_punts_maxims() {
            Proposta p = claus("DHCP\nDNS", "2").avalua("El DHCP assigna IPs i el DNS resol noms.");

            assertThat(p.score()).isEqualByComparingTo("2");
            assertThat(p.motius()).containsExactly("✓ Esmenta tots els conceptes clau");
        }

        @Test
        void concepte_absent_resta_el_seu_pes_i_ho_explica() {
            Proposta p = claus("DHCP | 1.5\nDNS | 0.5", "2").avalua("El DHCP assigna adreces.");

            assertThat(p.score()).isEqualByComparingTo("1.5");
            assertThat(p.motius()).containsExactly("−0,5: no esmenta «DNS»");
        }

        @Test
        void cap_concepte_dona_zero_amb_un_motiu_per_concepte() {
            Proposta p = claus("DHCP\nDNS", "2").avalua("No ho sé.");

            assertThat(p.score()).isEqualByComparingTo("0");
            assertThat(p.motius()).containsExactly("−1: no esmenta «DHCP»", "−1: no esmenta «DNS»");
        }

        @Test
        void qualsevol_alternativa_compta() {
            Proposta p = claus("adreça IP, direcció IP", "1").avalua("Cal configurar la direcció IP.");

            assertThat(p.score()).isEqualByComparingTo("1");
        }

        @Test
        void ignora_majuscules_i_accents() {
            Proposta p = claus("adreça IP\nmàscara de xarxa", "2")
                    .avalua("ADRECA ip i Mascara De Xarxa");

            assertThat(p.score()).isEqualByComparingTo("2");
        }

        @Test
        void ignora_puntuacio_i_espais_multiples() {
            Proposta p = claus("porta d'enllaç", "1").avalua("La porta   d’enllaç... és el router");

            assertThat(p.score()).isEqualByComparingTo("1");
        }

        @Test
        void compara_paraules_senceres() {
            // "IP" no ha de coincidir dins de "tipus" ni "DNS" dins de "DNSSEC"
            Proposta p = claus("IP\nDNS", "2").avalua("Hi ha diversos tipus de DNSSEC.");

            assertThat(p.score()).isEqualByComparingTo("0");
        }

        @Test
        void concepte_de_diverses_paraules_ha_d_apareixer_seguit() {
            Proposta p = claus("capa de xarxa", "1").avalua("La capa física i la xarxa local");

            assertThat(p.score()).isEqualByComparingTo("0");
        }

        @Test
        void l_ordre_dels_conceptes_no_importa() {
            Proposta p = claus("DHCP\nDNS", "2").avalua("DNS primer, després DHCP");

            assertThat(p.score()).isEqualByComparingTo("2");
        }

        @Test
        void concepte_al_principi_i_al_final_de_la_resposta() {
            Proposta p = claus("DHCP\nDNS", "2").avalua("DHCP i DNS");

            assertThat(p.score()).isEqualByComparingTo("2");
        }

        @Test
        void l_ela_geminada_coincideix() {
            Proposta p = claus("col·lisió", "1").avalua("Hi ha una col·lisió de paquets");

            assertThat(p.score()).isEqualByComparingTo("1");
        }

        @Test
        void numeros_com_a_conceptes() {
            Proposta p = claus("443\n80", "2").avalua("HTTPS usa el port 443; HTTP el 8080");

            assertThat(p.score()).isEqualByComparingTo("1");
            assertThat(p.motius()).containsExactly("−1: no esmenta «80»");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\n"})
        void resposta_buida_dona_zero_sense_resposta(String resposta) {
            Proposta p = claus("DHCP", "1").avalua(resposta);

            assertThat(p.score()).isEqualByComparingTo("0");
            assertThat(p.motius()).containsExactly("Sense resposta");
        }

        @Test
        void resposta_null_dona_zero_sense_resposta() {
            Proposta p = claus("DHCP", "1").avalua(null);

            assertThat(p.score()).isEqualByComparingTo("0");
            assertThat(p.feedback()).isEqualTo("Sense resposta");
        }

        @Test
        void la_suma_de_la_proposta_mai_supera_els_punts() {
            Proposta p = claus("a\nb\nc", "1").avalua("a b c");

            assertThat(p.score()).isEqualByComparingTo("1");
        }
    }

    // ── Format de punts ──────────────────────────────────────────────────────

    @Test
    void pts_usa_coma_decimal_i_treu_zeros() {
        assertThat(Proposta.pts(new BigDecimal("0.50"))).isEqualTo("0,5");
        assertThat(Proposta.pts(new BigDecimal("2.00"))).isEqualTo("2");
        assertThat(Proposta.pts(new BigDecimal("0.3333"))).isEqualTo("0,33");
        assertThat(Proposta.penalitzacio(new BigDecimal("1.25"), "motiu")).isEqualTo("−1,25: motiu");
    }
}
