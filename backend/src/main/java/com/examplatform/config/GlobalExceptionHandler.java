package com.examplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Retorna missatges d'error genèrics al client.
 * No s'exposen stack traces ni detalls interns.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Accés denegat"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Recurs no trobat"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthentication(
            org.springframework.security.core.AuthenticationException e) {
        String msg = e instanceof org.springframework.security.authentication.DisabledException
                ? "Aquest compte està desactivat"
                : "Correu o contrasenya incorrectes";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() != null ? e.getReason() : "Error"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Accés denegat"));
    }

    // ── Errors de la petició (del client): 4xx sense traça al registre ──────────────────────

    /** Cos JSON mal format o de tipus incorrecte. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Petició mal formada");
    }

    /** Dades que no compleixen la validació (@Valid): es retorna el primer camp erroni. */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        var camp = e.getBindingResult().getFieldError();
        return error(HttpStatus.BAD_REQUEST, camp != null
                ? "Camp «" + camp.getField() + "» invàlid: " + camp.getDefaultMessage()
                : "Dades invàlides");
    }

    /** Paràmetre de ruta o de consulta amb un format incorrecte (p. ex. un identificador que no és un UUID). */
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<Map<String, String>> handleParametre(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Paràmetres de la petició incorrectes");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMida(Exception e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "El fitxer és massa gran");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMetode(Exception e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Mètode no permès");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaType(Exception e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipus de contingut no admès");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoRecurs(Exception e) {
        return error(HttpStatus.NOT_FOUND, "Recurs no trobat");
    }

    /** Restricció de la BD (p. ex. esborrar alguna cosa que encara es fa servir). */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegritat(
            org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Operació rebutjada per una restricció de la BD: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "No es pot fer: hi ha dades que en depenen");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String missatge) {
        return ResponseEntity.status(status).body(Map.of("error", missatge));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        log.error("Error intern no controlat", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error intern del servidor"));
    }
}
