package com.examplatform.domain.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenciesImatgeTest {

    @Test
    void substitueix_totes_les_referencies_conegudes_i_deixa_la_resta() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), nouA = UUID.randomUUID(), altre = UUID.randomUUID();
        String text = "![x](fitxer:" + a + ") text ![y](fitxer:" + a + ") ![z](fitxer:" + b + ") ![w](fitxer:" + altre + ")";

        String r = ReferenciesImatge.remapeja(text, Map.of(a, nouA, b, b));

        assertThat(r).isEqualTo("![x](fitxer:" + nouA + ") text ![y](fitxer:" + nouA + ") ![z](fitxer:" + b
                + ") ![w](fitxer:" + altre + ")");
    }

    @Test
    void sense_ids_o_sense_text_no_fa_res() {
        assertThat(ReferenciesImatge.remapeja(null, Map.of(UUID.randomUUID(), UUID.randomUUID()))).isNull();
        assertThat(ReferenciesImatge.remapeja("hola", Map.of())).isEqualTo("hola");
    }
}
