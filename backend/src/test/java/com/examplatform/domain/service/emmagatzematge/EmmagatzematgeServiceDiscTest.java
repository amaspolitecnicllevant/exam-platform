package com.examplatform.domain.service.emmagatzematge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmmagatzematgeServiceDiscTest {

    @TempDir Path tmp;

    private static void fitxer(Path p, int bytes) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[bytes]);
    }

    @Test
    void mesura_els_fitxers_de_preguntes_i_els_de_lliuraments_per_separat() throws Exception {
        fitxer(tmp.resolve("questions/q1/dades.txt"), 100);
        fitxer(tmp.resolve("questions/q2/a.txt"), 50);
        fitxer(tmp.resolve("answers/s1/x.docx"), 1000);
        fitxer(tmp.resolve("answers/s1/y.pkt"), 500);
        fitxer(tmp.resolve("answers/s2/z.docx"), 250);
        fitxer(tmp.resolve("altres/no-compta.bin"), 9999);     // fora de les carpetes auditades

        var disc = new EmmagatzematgeService(null, tmp.toString()).disc();

        assertThat(disc.midaPreguntesDisc()).isEqualTo(150);
        assertThat(disc.midaLliuramentsDisc()).isEqualTo(1750);
    }

    @Test
    void sense_carpetes_tot_es_zero_i_encara_s_informa_de_l_espai_lliure() {
        var disc = new EmmagatzematgeService(null, tmp.resolve("no-existeix-encara").toString()).disc();

        assertThat(disc.midaPreguntesDisc()).isZero();
        assertThat(disc.midaLliuramentsDisc()).isZero();
        assertThat(disc.espaiTotal()).isNotNull().isPositive();
        assertThat(disc.espaiLliure()).isNotNull().isNotNegative().isLessThanOrEqualTo(disc.espaiTotal());
    }

    @Test
    void midaDirectori_no_falla_amb_un_directori_inexistent() {
        assertThat(EmmagatzematgeService.midaDirectori(tmp.resolve("res"))).isZero();
    }
}
