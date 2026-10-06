package com.examplatform.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitxersRespostaStorageTest {

    @TempDir Path tmp;
    FitxersRespostaStorage storage;
    final UUID sessio = UUID.randomUUID();
    final UUID pregunta = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new FitxersRespostaStorage(tmp.toString());
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private long fitxersAlDisc() throws IOException {
        Path dir = tmp.resolve("answers");
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void desa_el_contingut_amb_mida_empremta_i_capcalera() throws Exception {
        String contingut = "PK\u0003\u0004 contingut del document";

        var d = storage.desa(sessio, pregunta, "docx", bytes(contingut), 1024);

        assertThat(Files.readString(d.ruta(), StandardCharsets.ISO_8859_1)).isEqualTo(contingut);
        assertThat(d.mida()).isEqualTo(contingut.length());
        String esperat = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(contingut.getBytes(StandardCharsets.ISO_8859_1)));
        assertThat(d.sha256()).isEqualTo(esperat);
        assertThat(d.capcaleraLlargada()).isEqualTo(contingut.length());
        assertThat(d.capcalera()[0]).isEqualTo((byte) 'P');
    }

    @Test
    void el_nom_al_disc_el_genera_el_sistema_dins_la_carpeta_de_la_sessio() throws Exception {
        var d = storage.desa(sessio, pregunta, "pkt", bytes("x"), 1024);

        assertThat(d.ruta().getParent()).isEqualTo(storage.dirSessio(sessio));
        assertThat(d.ruta().getFileName().toString()).startsWith(pregunta.toString()).endsWith(".pkt");
    }

    @Test
    void dos_lliuraments_de_la_mateixa_pregunta_no_se_sobreescriuen() throws Exception {
        var a = storage.desa(sessio, pregunta, "docx", bytes("un"), 1024);
        var b = storage.desa(sessio, pregunta, "docx", bytes("dos"), 1024);

        assertThat(a.ruta()).isNotEqualTo(b.ruta());
        assertThat(fitxersAlDisc()).isEqualTo(2);
    }

    @Test
    void la_capcalera_son_nomes_els_primers_bytes_en_fitxers_grans() throws Exception {
        String gran = "A".repeat(5000);

        var d = storage.desa(sessio, pregunta, "txt", bytes(gran), 10_000);

        assertThat(d.mida()).isEqualTo(5000);
        assertThat(d.capcaleraLlargada()).isEqualTo(1024);
    }

    @Test
    void si_supera_la_mida_maxima_no_en_queda_res_al_disc() throws Exception {
        assertThatThrownBy(() -> storage.desa(sessio, pregunta, "docx", bytes("X".repeat(2000)), 1000))
                .isInstanceOf(FitxersRespostaStorage.MassaGran.class)
                .hasMessageContaining("mida màxima");

        assertThat(fitxersAlDisc()).isZero();
    }

    @Test
    void una_mida_exactament_igual_al_maxim_es_accepta() throws Exception {
        var d = storage.desa(sessio, pregunta, "txt", bytes("X".repeat(1000)), 1000);

        assertThat(d.mida()).isEqualTo(1000);
    }

    @Test
    void esborra_un_fitxer_propi() throws Exception {
        var d = storage.desa(sessio, pregunta, "docx", bytes("x"), 1024);

        storage.esborra(d.ruta().toString());

        assertThat(Files.exists(d.ruta())).isFalse();
    }

    @Test
    void no_esborra_mai_fitxers_fora_de_la_carpeta_de_respostes() throws Exception {
        Path alie = Files.writeString(tmp.resolve("important.txt"), "no m'esborris");

        storage.esborra(alie.toString());
        storage.esborra(tmp.resolve("answers").resolve("..").resolve("important.txt").toString());   // intent de sortir amb ..
        storage.esborra("/etc/hostname");

        assertThat(Files.exists(alie)).isTrue();
    }

    @Test
    void esborra_ruta_nula_o_inexistent_no_falla() {
        storage.esborra(null);
        storage.esborra(tmp.resolve("answers/no-existeix.docx").toString());
    }

    @Test
    void esborraSessio_treu_tots_els_fitxers_de_la_sessio_pero_no_els_d_altres() throws Exception {
        UUID altra = UUID.randomUUID();
        storage.desa(sessio, pregunta, "docx", bytes("a"), 1024);
        storage.desa(sessio, UUID.randomUUID(), "pkt", bytes("b"), 1024);
        var daltra = storage.desa(altra, pregunta, "docx", bytes("c"), 1024);

        storage.esborraSessio(sessio);

        assertThat(Files.exists(storage.dirSessio(sessio))).isFalse();
        assertThat(Files.exists(daltra.ruta())).isTrue();
    }

    @Test
    void esborraSessio_d_una_sessio_sense_fitxers_no_falla() {
        storage.esborraSessio(UUID.randomUUID());
    }
}
