package com.examplatform.domain.service;

import com.examplatform.dto.ProgramacioDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramacioCsvParserTest {

    @Test
    void llegeix_unitats_i_competencies_amb_punt_i_coma_i_capcalera() {
        String csv = "unitat;competència;descripció;nom de la unitat\n"
                + "UT01;CO1;Instal·la sistemes;Instal·lació\n"
                + "UT01;CO2;Configura xarxes;\n"
                + "UT02;co2;Configura xarxes;Xarxes\n";

        ProgramacioDto.Peticio p = ProgramacioCsvParser.llegeix(csv);

        assertThat(p.competencies()).extracting(ProgramacioDto.CompetenciaPeticio::codi).containsExactly("CO1", "CO2");
        assertThat(p.competencies().get(0).descripcio()).isEqualTo("Instal·la sistemes");
        assertThat(p.unitats()).extracting(ProgramacioDto.UnitatPeticio::codi).containsExactly("UT01", "UT02");
        assertThat(p.unitats().get(0).competencies()).containsExactly("CO1", "CO2");
        assertThat(p.unitats().get(0).nom()).isEqualTo("Instal·lació");
        assertThat(p.unitats().get(1).competencies()).containsExactly("CO2");
        assertThat(p.unitats().get(1).nom()).isEqualTo("Xarxes");
    }

    @Test
    void detecta_comes_i_tabuladors_i_no_cal_capcalera() {
        assertThat(ProgramacioCsvParser.llegeix("UT01,CO1\nUT02,CO2").unitats()).hasSize(2);
        assertThat(ProgramacioCsvParser.llegeix("UT01\tCO1\tx\nUT01\tCO2\ty").unitats().get(0).competencies())
                .containsExactly("CO1", "CO2");
    }

    @Test
    void ignora_el_bom_les_linies_buides_i_les_parelles_repetides() {
        ProgramacioDto.Peticio p = ProgramacioCsvParser.llegeix("﻿UT01;CO1\n\nUT01;CO1\n");

        assertThat(p.unitats()).hasSize(1);
        assertThat(p.unitats().get(0).competencies()).containsExactly("CO1");
    }

    @Test
    void una_competencia_amb_dues_descripcions_diferents_es_rebutja() {
        assertThatThrownBy(() -> ProgramacioCsvParser.llegeix("UT01;CO1;una\nUT02;CO1;una altra"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fila 2").hasMessageContaining("CO1");
    }

    @Test
    void les_files_incompletes_i_el_csv_buit_es_rebutgen() {
        assertThatThrownBy(() -> ProgramacioCsvParser.llegeix("UT01;CO1\nUT02;"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fila 2");
        assertThatThrownBy(() -> ProgramacioCsvParser.llegeix("  \n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgramacioCsvParser.llegeix("unitat;competència\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
