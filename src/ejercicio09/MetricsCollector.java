package ejercicio09;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/**
 * Singleton que recolecta métricas de rendimiento de toda la aplicación.
 * Utiliza estructuras concurrentes y limita la cantidad de métricas distintas.
 */
public class MetricsCollector {

    private static final int MAX_METRICS = 100;

    private final Map<String, LongAdder> counters =
            new ConcurrentHashMap<>();

    private final Map<String, LatencyStats> latencies =
            new ConcurrentHashMap<>();

    private final LongAdder droppedMetrics = new LongAdder();

    // Reserva capacidad para un máximo de 100 nombres de métricas
    private final Semaphore capacity = new Semaphore(MAX_METRICS);

    // Coordina el registro con la operación de exportar y limpiar
    private final Object resetLock = new Object();

    private MetricsCollector() {
    }

    // Holder Idiom: instancia perezosa y segura entre hilos
    private static class Holder {
        private static final MetricsCollector INSTANCE =
                new MetricsCollector();
    }

    public static MetricsCollector getInstance() {
        return Holder.INSTANCE;
    }

    // Incrementa un contador de rendimiento
    public void increment(String name) {
        if (!isValidName(name)) {
            droppedMetrics.increment();
            return;
        }

        synchronized (resetLock) {
            LongAdder counter = counters.get(name);

            if (counter == null) {
                if (latencies.containsKey(name)) {
                    droppedMetrics.increment();
                    return;
                }

                if (!capacity.tryAcquire()) {
                    droppedMetrics.increment();
                    return;
                }

                counter = new LongAdder();
                counters.put(name, counter);
            }

            counter.increment();
        }
    }

    // Registra una latencia en milisegundos
    public void recordLatency(String name, long millis) {
        if (!isValidName(name) || millis < 0) {
            droppedMetrics.increment();
            return;
        }

        synchronized (resetLock) {
            LatencyStats stats = latencies.get(name);

            if (stats == null) {
                if (counters.containsKey(name)) {
                    droppedMetrics.increment();
                    return;
                }

                if (!capacity.tryAcquire()) {
                    droppedMetrics.increment();
                    return;
                }

                stats = new LatencyStats();
                latencies.put(name, stats);
            }

            stats.record(millis);
        }
    }

    // Comprueba que el nombre de la métrica sea válido
    private boolean isValidName(String name) {
        return name != null && !name.isBlank();
    }

    // Exporta un resumen sin borrar las métricas acumuladas
    public String export() {
        synchronized (resetLock) {
            return buildSummary();
        }
    }

    // Exporta el resumen y después limpia los datos acumulados
    public String exportAndReset() {
        synchronized (resetLock) {
            String summary = buildSummary();

            counters.clear();
            latencies.clear();
            droppedMetrics.reset();
            capacity.release(MAX_METRICS - capacity.availablePermits());

            return summary;
        }
    }

    // Construye el resumen ordenado alfabéticamente
    private String buildSummary() {
        StringBuilder summary =
                new StringBuilder("=== Resumen de métricas ===\n");

        new TreeMap<>(counters).forEach((name, value) ->
                summary.append(String.format(
                        "contador %-20s %d%n",
                        name,
                        value.sum()
                ))
        );

        new TreeMap<>(latencies).forEach((name, stats) ->
                summary.append(String.format(
                        "latencia %-20s %s%n",
                        name,
                        stats
                ))
        );

        summary.append(
                "métricas descartadas (capacidad alcanzada o datos inválidos): "
        ).append(droppedMetrics.sum());

        return summary.toString();
    }
}