package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.service.exportacio.RevisioIaParser.Fila;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevisioIaParserTest {

    private static List<Fila> analitza(String text) {
        return RevisioIaParser.analitza(text);
    }

    @Test
    void llegeix_un_csv_amb_punt_i_coma_i_capcalera() {
        List<Fila> files = analitza("""
                alumne;pregunta;nota;justificacio
                Alumne 7F3A2C;1;1.5;Bona resposta.
                Alumne 7F3A2C;2;0;No fa el que demana.
                """);

        assertThat(files).hasSize(2);
        assertThat(files.get(0)).isEqualTo(new Fila(2, "Alumne 7F3A2C", "1", "1.5", "Bona resposta."));
        assertThat(files.get(1).nota()).isEqualTo("0");
        assertThat(files.get(1).linia()).isEqualTo(3);
    }

    @Test
    void les_columnes_de_la_capcalera_poden_anar_en_un_altre_ordre_i_amb_accents_i_majuscules() {
        List<Fila> files = analitza("""
                Pregunta;Justificació;Nota;Alumne
                3;Molt bé;2,5;Alumne AAAAAA
                """);

        assertThat(files).containsExactly(new Fila(2, "Alumne AAAAAA", "3", "2,5", "Molt bé"));
    }

    @Test
    void sense_capcalera_s_entenen_les_quatre_columnes_per_ordre() {
        List<Fila> files = analitza("Alumne AAAAAA;4;1;Correcte\nAlumne BBBBBB;4;0;Incorrecte");

        assertThat(files).extracting(Fila::alumne).containsExactly("Alumne AAAAAA", "Alumne BBBBBB");
        assertThat(files).extracting(Fila::justificacio).containsExactly("Correcte", "Incorrecte");
    }

    @Test
    void la_justificacio_es_opcional() {
        List<Fila> files = analitza("alumne;pregunta;nota\nAlumne AAAAAA;1;2");

        assertThat(files).containsExactly(new Fila(2, "Alumne AAAAAA", "1", "2", ""));
    }

    @Test
    void ignora_el_text_de_la_ia_abans_i_despres_i_les_tanques_del_bloc_de_codi() {
        List<Fila> files = analitza("""
                Aquí tens la revisió, amb les notes que m'has demanat.

                ```csv
                alumne;pregunta;nota;justificacio
                Alumne AAAAAA;1;1;Bé
                ```

                Si vols que canviï algun criteri, avisa'm.
                """);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).alumne()).isEqualTo("Alumne AAAAAA");
    }

    @Test
    void accepta_una_taula_markdown_amb_separador_i_pipes_escapats() {
        List<Fila> files = analitza("""
                | alumne | pregunta | nota | justificacio |
                |:--|--:|--:|:--|
                | Alumne AAAAAA | P1 | 1,5 | Fa servir \\| correctament |
                | Alumne AAAAAA | P2 | 0 | |
                """);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).pregunta()).isEqualTo("P1");
        assertThat(files.get(0).nota()).isEqualTo("1,5");
        assertThat(files.get(0).justificacio()).isEqualTo("Fa servir | correctament");
        assertThat(files.get(1).justificacio()).isEmpty();
    }

    @Test
    void accepta_comes_i_tabuladors_com_a_separador() {
        assertThat(analitza("alumne,pregunta,nota,justificacio\nAlumne AAAAAA,1,2,Bé")).hasSize(1);
        assertThat(analitza("alumne\tpregunta\tnota\tjustificacio\nAlumne AAAAAA\t1\t2\tBé")).hasSize(1);
    }

    @Test
    void en_un_csv_de_comes_la_justificacio_entre_cometes_pot_portar_comes_i_cometes_dobles() {
        List<Fila> files = analitza("alumne,pregunta,nota,justificacio\nAlumne AAAAAA,1,2,\"Bé, però \"\"just\"\"\"");

        assertThat(files.get(0).justificacio()).isEqualTo("Bé, però \"just\"");
    }

    @Test
    void en_un_csv_de_punt_i_coma_la_justificacio_pot_portar_comes() {
        List<Fila> files = analitza("alumne;pregunta;nota;justificacio\nAlumne AAAAAA;1;2;Bé, molt bé, perfecte");

        assertThat(files.get(0).justificacio()).isEqualTo("Bé, molt bé, perfecte");
    }

    @Test
    void una_capcalera_repetida_i_les_files_sense_pregunta_numerica_no_son_dades() {
        List<Fila> files = analitza("""
                alumne;pregunta;nota;justificacio
                Alumne AAAAAA;1;1;Bé
                alumne;pregunta;nota;justificacio
                Total;global;aprovat;bona feina
                Alumne AAAAAA;2;0;Malament
                """);

        assertThat(files).extracting(Fila::pregunta).containsExactly("1", "2");
    }

    @Test
    void tolera_crlf_bom_i_linies_buides() {
        List<Fila> files = analitza("﻿alumne;pregunta;nota;justificacio\r\n\r\nAlumne AAAAAA;1;1;Bé\r\n\r\n");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).linia()).isEqualTo(3);
    }

    @Test
    void no_peta_amb_cometes_mal_tancades() {
        List<Fila> files = analitza("alumne;pregunta;nota;justificacio\nAlumne AAAAAA;1;1;\"sense tancar");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).nota()).isEqualTo("1");
    }

    @Test
    void text_buit_o_sense_files_no_dona_res() {
        assertThat(analitza(null)).isEmpty();
        assertThat(analitza("")).isEmpty();
        assertThat(analitza("Hola, no tinc cap nota per donar-te; perdona.")).isEmpty();
        assertThat(analitza("alumne;pregunta;nota;justificacio")).isEmpty();
    }

    @Test
    void rebutja_un_text_massa_gran() {
        String gran = "x".repeat(RevisioIaParser.MAX_CARACTERS + 1);

        assertThatThrownBy(() -> analitza(gran)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("massa gran");
    }

    @Test
    void les_instruccions_de_l_exportacio_demanen_el_format_que_aquest_analitzador_llegeix() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(com.examplatform.domain.model.QuestionType.SHORT, "10", null);

        String md = ExportacioMarkdown.respostes(f.dades(), new ExportacioMarkdown.Opcions(true, true), java.time.LocalDate.of(2026, 10, 9));
        // L'exemple que veu la IA s'ha d'entendre tal qual
        String exemple = md.substring(md.indexOf("```csv") + 6, md.indexOf("```", md.indexOf("```csv") + 6));

        assertThat(analitza(exemple)).hasSize(2);
        assertThat(analitza(exemple).get(0).alumne()).isEqualTo("Alumne 7F3A2C");
    }

    @Test
    void l_exemple_de_l_exportacio_amb_noms_usa_el_correu_i_tampoc_conte_codis_anonims() {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(com.examplatform.domain.model.QuestionType.SHORT, "10", null);

        String md = ExportacioMarkdown.respostes(f.dades(), new ExportacioMarkdown.Opcions(false, true), java.time.LocalDate.of(2026, 10, 9));
        String exemple = md.substring(md.indexOf("```csv") + 6, md.indexOf("```", md.indexOf("```csv") + 6));

        assertThat(analitza(exemple)).hasSize(2);
        assertThat(analitza(exemple).get(0).alumne()).isEqualTo("maria@centre.cat");
        assertThat(md).doesNotContain("Alumne 7F3A2C");
    }
}
