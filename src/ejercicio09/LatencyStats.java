package ejercicio09;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Guarda valores agregados (cantidad, total y máximo).
 * La memoria no crece con cada muestra registrada.
 */

class LatencyStats {

    private final LongAdder count = new LongAdder();
    private final LongAdder totalMillis = new LongAdder();
    private final AtomicLong maxMillis = new AtomicLong();

    // Registra una medición de latencia en milisegundos
    void record(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException(
                    "La latencia no puede ser negativa."
            );
        }

        count.increment();
        totalMillis.add(millis);
        maxMillis.accumulateAndGet(millis, Math::max);
    }

    @Override
    public String toString() {
        long samples = count.sum();
        double average = samples == 0
                ? 0.0
                : (double) totalMillis.sum() / samples;

        return String.format(
                "muestras=%d promedio=%.1fms máximo=%dms",
                samples,
                average,
                maxMillis.get()
        );
    }
}