package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExportacioFitxersTest {

    private static final LocalDate DATA = LocalDate.of(2026, 10, 6);
    @TempDir Path tmp;

    private Answer lliura(FixtureExamen f, ExamSession s, Question q, String nom, String contingut) throws Exception {
        Path ruta = Files.writeString(tmp.resolve(UUID.randomUUID() + ".bin"), contingut);
        Answer a = f.resposta(s, q, nom, null);
        a.setFitxerNom(nom);
        a.setFitxerRuta(ruta.toString());
        return a;
    }

    private static Map<String, String> llegeixZip(byte[] zip) throws Exception {
        Map<String, String> res = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) res.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        return res;
    }

    private static byte[] genera(ExportacioFitxers.Pla pla) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacioFitxers.escriu(out, pla);
        return out.toByteArray();
    }

    @Test
    void un_directori_per_alumne_amb_el_fitxer_de_cada_pregunta() throws Exception {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.SHORT, "2", null);
        Question p2 = f.pregunta(QuestionType.FILE_UPLOAD, "4", null);
        Question p3 = f.pregunta(QuestionType.FILE_UPLOAD, "4", null);
        var anna = f.entregat("Anna Soler", "anna@x.cat");
        lliura(f, anna, p2, "Informe.docx", "contingut de l'informe");
        lliura(f, anna, p3, "xarxa.pkt", "simulació");
        var berta = f.entregat("Berta Pons", "berta@x.cat");
        lliura(f, berta, p2, "Informe.docx", "l'informe de la Berta");

        Map<String, String> zip = llegeixZip(genera(ExportacioFitxers.pla(f.dades(), DATA)));

        assertThat(zip.keySet()).containsExactly("LLEGEIX-ME.txt", "Anna Soler (anna)/P2-Informe.docx",
                "Anna Soler (anna)/P3-xarxa.pkt", "Berta Pons (berta)/P2-Informe.docx");
        assertThat(zip.get("Anna Soler (anna)/P2-Informe.docx")).isEqualTo("contingut de l'informe");
        assertThat(zip.get("Berta Pons (berta)/P2-Informe.docx")).isEqualTo("l'informe de la Berta");
    }

    @Test
    void nomes_inclou_preguntes_de_fitxer_amb_lliurament() throws Exception {
        FixtureExamen f = new FixtureExamen();
        Question curta = f.pregunta(QuestionType.SHORT, "5", null);
        Question fitxer = f.pregunta(QuestionType.FILE_UPLOAD, "5", null);
        var anna = f.entregat("Anna", "a@x.cat");
        f.resposta(anna, curta, "text", null);
        f.entregat("Berta", "b@x.cat");                 // sense resposta
        f.resposta(f.entregat("Carla", "c@x.cat"), fitxer, null, null);   // resposta sense fitxer

        assertThat(ExportacioFitxers.pla(f.dades(), DATA).entrades()).isEmpty();
    }

    @Test
    void sense_cap_lliurament_el_zip_te_nomes_el_llegeix_me_que_ho_diu() throws Exception {
        FixtureExamen f = new FixtureExamen();
        f.pregunta(QuestionType.FILE_UPLOAD, "10", null);
        f.entregat("Anna", "a@x.cat");

        Map<String, String> zip = llegeixZip(genera(ExportacioFitxers.pla(f.dades(), DATA)));

        assertThat(zip.keySet()).containsExactly("LLEGEIX-ME.txt");
        assertThat(zip.get("LLEGEIX-ME.txt")).contains("Cap alumne ha lliurat cap fitxer").contains("Fitxers inclosos: 0");
    }

    @Test
    void un_fitxer_que_ja_no_es_al_disc_s_avisa_al_llegeix_me_i_no_trenca_el_zip() throws Exception {
        FixtureExamen f = new FixtureExamen();
        Question p = f.pregunta(QuestionType.FILE_UPLOAD, "10", null);
        var anna = f.entregat("Anna", "a@x.cat");
        Answer perduda = lliura(f, anna, p, "Perduda.docx", "x");
        Files.delete(Path.of(perduda.getFitxerRuta()));
        var berta = f.entregat("Berta", "b@x.cat");
        lliura(f, berta, p, "Bona.docx", "ok");

        Map<String, String> zip = llegeixZip(genera(ExportacioFitxers.pla(f.dades(), DATA)));

        assertThat(zip.keySet()).contains("Berta (b)/P1-Bona.docx").doesNotContain("Anna (a)/P1-Perduda.docx");
        assertThat(zip.get("LLEGEIX-ME.txt")).contains("AVÍS").contains("Anna · P1 · Perduda.docx");
    }

    @Test
    void els_noms_d_alumne_i_de_fitxer_no_poden_sortir_del_directori() throws Exception {
        FixtureExamen f = new FixtureExamen();
        Question p = f.pregunta(QuestionType.FILE_UPLOAD, "10", null);
        var s = f.entregat("../../Windows/System32", "../../x@x.cat");
        lliura(f, s, p, "../../../etc/passwd", "x");

        var pla = ExportacioFitxers.pla(f.dades(), DATA);

        assertThat(pla.entrades()).hasSize(1);
        String nom = pla.entrades().get(0).nomZip();
        assertThat(nom).doesNotContain("..").doesNotStartWith("/").doesNotContain("\\");
        assertThat(nom.chars().filter(c -> c == '/').count()).isEqualTo(1);   // només «directori/fitxer»
    }

    @Test
    void dos_fitxers_amb_el_mateix_nom_no_se_sobreescriuen() throws Exception {
        FixtureExamen f = new FixtureExamen();
        Question p = f.pregunta(QuestionType.FILE_UPLOAD, "10", null);
        // Mateix nom i mateix correu: abans de ser únics tindrien el mateix directori i fitxer
        lliura(f, f.entregat("Anna", "a@x.cat"), p, "x.docx", "primer");
        lliura(f, f.entregat("Anna", "a@x.cat"), p, "x.docx", "segon");

        Map<String, String> zip = llegeixZip(genera(ExportacioFitxers.pla(f.dades(), DATA)));

        assertThat(zip).hasSize(3);
        assertThat(zip.values()).contains("primer", "segon");
    }

    @Test
    void el_llegeix_me_porta_el_titol_la_data_i_el_recompte() throws Exception {
        FixtureExamen f = new FixtureExamen();
        f.exam.setTitle("Pràctica\nPacket Tracer");
        Question p = f.pregunta(QuestionType.FILE_UPLOAD, "10", null);
        lliura(f, f.entregat("Anna", "a@x.cat"), p, "x.pkt", "1");

        String text = ExportacioFitxers.llegeixMe(ExportacioFitxers.pla(f.dades(), DATA));

        assertThat(text).contains("«Pràctica Packet Tracer»").contains("2026-10-06").contains("Fitxers inclosos: 1");
    }
}
