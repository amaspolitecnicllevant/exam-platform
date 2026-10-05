package com.examplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Límit global d'execucions de codi interactives (botó ▶ Executar d'alumnes i professors).
 * Evita que una classe sencera executant alhora saturi la màquina. La correcció en segon pla
 * no hi passa: ja té el seu propi pool limitat ({@code correction.async-threads}).
 */
@Component
public class ExecucionsInteractives {

    private final Semaphore permisos;
    private final int esperaMaxSegons;

    public ExecucionsInteractives(@Value("${execution.max-concurrent:4}") int maxConcurrent,
                                  @Value("${execution.queue-wait-seconds:30}") int esperaMaxSegons) {
        this.permisos = new Semaphore(maxConcurrent, true);
        this.esperaMaxSegons = esperaMaxSegons;
    }

    public <T> T executa(Supplier<T> execucio) {
        boolean adquirit;
        try {
            adquirit = permisos.tryAcquire(esperaMaxSegons, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Execució interrompuda");
        }
        if (!adquirit) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servidor està executant massa codi alhora. Torna-ho a provar d'aquí a uns segons.");
        }
        try {
            return execucio.get();
        } finally {
            permisos.release();
        }
    }
}
