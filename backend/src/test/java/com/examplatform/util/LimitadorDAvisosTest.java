package com.examplatform.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorDAvisosTest {

    final LimitadorDAvisos limitador = new LimitadorDAvisos(Duration.ofMinutes(10));

    @Test
    void el_primer_avis_passa_i_els_seguents_de_la_finestra_no() {
        assertThat(limitador.permet("ip1", 0)).isTrue();
        assertThat(limitador.permet("ip1", 5 * 60_000)).isFalse();
        assertThat(limitador.permet("ip1", 9 * 60_000 + 59_000)).isFalse();
    }

    @Test
    void passada_la_finestra_torna_a_passar() {
        assertThat(limitador.permet("ip1", 0)).isTrue();
        assertThat(limitador.permet("ip1", 10 * 60_000)).isTrue();
        assertThat(limitador.permet("ip1", 11 * 60_000)).isFalse();   // la finestra compta des de l'últim que ha passat
    }

    @Test
    void cada_clau_te_la_seva_finestra() {
        assertThat(limitador.permet("ip1", 0)).isTrue();
        assertThat(limitador.permet("ip2", 1)).isTrue();
        assertThat(limitador.permet("ip1", 2)).isFalse();
    }

    @Test
    void amb_moltes_claus_inventades_no_creix_sense_limit() {
        for (int i = 0; i < 25_000; i++) limitador.permet("clau" + i, 0);

        assertThat(limitador.permet("clau0", 1)).isTrue();   // s'ha buidat en superar el màxim: l'avís torna a passar
    }

    @Test
    void el_text_per_a_l_auditoria_no_porta_caracters_de_control_ni_es_desmesura() {
        assertThat(LimitadorDAvisos.net("anna\n@x.cat\r\t", 100)).isEqualTo("anna@x.cat");
        assertThat(LimitadorDAvisos.net("  Anna@X.cat ", 100)).isEqualTo("Anna@X.cat");
        assertThat(LimitadorDAvisos.net("x".repeat(500), 100)).hasSize(100);
        assertThat(LimitadorDAvisos.net(null, 100)).isEmpty();
    }
}
