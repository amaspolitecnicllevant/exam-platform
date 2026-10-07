package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.exportacio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exportacions d'un examen: notes (CSV i Excel), notes per RA, respostes en Markdown per a una IA
 * (anònimes per defecte, amb el fitxer de claus a part), informe i ZIP amb els fitxers lliurats.
 * Qui exporta ha de poder gestionar l'examen, i cada exportació queda al registre d'activitat.
 */
@RestController
@RequestMapping("/api/export/exam/{examId}")
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
@RequiredArgsConstructor
public class ExportacioController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown; charset=UTF-8");
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacioService exportacio;
    private final AuditLogService auditLog;

    @GetMapping("/notes.csv")
    public ResponseEntity<byte[]> notes(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, "notes", examId);
        return fitxer(ExportacioNotes.notesCsv(d), d, "notes.csv", CSV);
    }

    @GetMapping("/notes-ra.csv")
    public ResponseEntity<byte[]> notesRa(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, "notes per RA", examId);
        return fitxer(ExportacioNotes.raCsv(d), d, "notes-ra.csv", CSV);
    }

    /** Respostes en Markdown per passar a una IA. Anònimes per defecte: només amb {@code anonim=false} porten nom i correu. */
    @GetMapping("/respostes.md")
    public ResponseEntity<byte[]> respostes(@PathVariable UUID examId,
                                            @RequestParam(defaultValue = "true") boolean anonim,
                                            @RequestParam(defaultValue = "true") boolean model,
                                            @AuthenticationPrincipal User user) {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, anonim ? "respostes en Markdown (anònim)" : "respostes en Markdown (AMB NOMS)", examId);
        String md = ExportacioMarkdown.respostes(d, new ExportacioMarkdown.Opcions(anonim, model), LocalDate.now());
        return fitxer(md, d, anonim ? "respostes-anonimes.md" : "respostes.md", MARKDOWN);
    }

    /** Fitxer de claus: «Alumne 7F3A2C» ↔ alumne real, per tornar a lligar el que proposi la IA. */
    @GetMapping("/clau-alumnes.csv")
    public ResponseEntity<byte[]> clauAlumnes(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, "clau d'alumnes anònims", examId);
        return fitxer(ExportacioMarkdown.clauAlumnesCsv(d), d, "clau-alumnes.csv", CSV);
    }

    @GetMapping("/informe.md")
    public ResponseEntity<byte[]> informe(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, "informe", examId);
        return fitxer(ExportacioMarkdown.informe(d, exportacio.estadistiques(examId, user), LocalDate.now()),
                d, "informe.md", MARKDOWN);
    }

    @GetMapping("/examen.xlsx")
    public ResponseEntity<byte[]> excel(@PathVariable UUID examId, @AuthenticationPrincipal User user) throws IOException {
        DadesExamen d = exportacio.dades(examId, user);
        auditar(user, "Excel", examId);
        return adjunt(ExportacioExcel.excel(d), d.titolPerFitxer() + ".xlsx", XLSX);
    }

    /** ZIP amb els fitxers lliurats, escrit en flux: pot ser molt gran. */
    @GetMapping("/fitxers.zip")
    public ResponseEntity<StreamingResponseBody> fitxers(@PathVariable UUID examId, @AuthenticationPrincipal User user) {
        ExportacioFitxers.Pla pla = exportacio.plaFitxers(examId, user);
        auditar(user, "ZIP de lliuraments (" + pla.entrades().size() + " fitxers)", examId);
        String nom = com.examplatform.domain.service.exportacio.DadesExamen.nomNet(pla.titol(), 60).replace(' ', '_') + "_lliuraments.zip";
        StreamingResponseBody cos = out -> ExportacioFitxers.escriu(out, pla);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(cos);
    }

    private void auditar(User user, String que, UUID examId) {
        auditLog.log(user.getId(), "EXAM_EXPORTED", que + " · examen " + examId);
    }

    private ResponseEntity<byte[]> fitxer(String text, DadesExamen d, String sufix, MediaType tipus) {
        return adjunt(text.getBytes(StandardCharsets.UTF_8), d.titolPerFitxer() + "_" + sufix, tipus);
    }

    private ResponseEntity<byte[]> adjunt(byte[] bytes, String nom, MediaType tipus) {
        return ResponseEntity.ok()
                .contentType(tipus)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }
}
