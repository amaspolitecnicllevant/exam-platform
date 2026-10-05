package com.examplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecucionsInteractivesTest {

    @Test
    void mai_hi_ha_mes_execucions_simultanies_que_el_limit() throws Exception {
        ExecucionsInteractives limit = new ExecucionsInteractives(2, 10);
        AtomicInteger actives = new AtomicInteger();
        AtomicInteger maxim = new AtomicInteger();
        CountDownLatch fi = new CountDownLatch(6);
        for (int i = 0; i < 6; i++) {
            new Thread(() -> {
                limit.executa(() -> {
                    maxim.accumulateAndGet(actives.incrementAndGet(), Math::max);
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    actives.decrementAndGet();
                    return null;
                });
                fi.countDown();
            }).start();
        }
        fi.await();

        assertThat(maxim.get()).isEqualTo(2);
    }

    @Test
    void si_no_hi_ha_torn_a_temps_respon_servidor_ocupat() throws Exception {
        ExecucionsInteractives limit = new ExecucionsInteractives(1, 0);
        CountDownLatch dins = new CountDownLatch(1);
        CountDownLatch surt = new CountDownLatch(1);
        Thread ocupant = new Thread(() -> limit.executa(() -> {
            dins.countDown();
            try { surt.await(); } catch (InterruptedException ignored) {}
            return null;
        }));
        ocupant.start();
        dins.await();

        assertThatThrownBy(() -> limit.executa(() -> "no"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("massa codi alhora");
        surt.countDown();
        ocupant.join();
    }

    @Test
    void allibera_el_torn_encara_que_l_execucio_falli() {
        ExecucionsInteractives limit = new ExecucionsInteractives(1, 0);
        assertThatThrownBy(() -> limit.executa(() -> { throw new IllegalStateException("falla"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(limit.executa(() -> "ok")).isEqualTo("ok");
    }
}
