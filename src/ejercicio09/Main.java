package ejercicio09;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        List<Thread> workers = new ArrayList<>();

        // Cuatro hilos simulan interceptores de red concurrentes
        for (int t = 0; t < 4; t++) {
            Thread worker = new Thread(() -> {
                MetricsCollector metrics = MetricsCollector.getInstance();

                for (int i = 1; i <= 1000; i++) {
                    metrics.increment("http.solicitudes");
                    metrics.recordLatency("http.latencia", i % 50);
                }
            });

            workers.add(worker);
            worker.start();
        }

        // Esperamos a que todos los hilos terminen
        for (Thread worker : workers) {
            worker.join();
        }

        MetricsCollector metrics = MetricsCollector.getInstance();

        System.out.println(metrics.export());
        System.out.println(
                "\nComprobación: se esperaban 4000 solicitudes registradas."
        );

        // Intentamos registrar más métricas de las permitidas
        for (int i = 0; i < 150; i++) {
            metrics.increment("metrica.extra." + i);
        }

        String resetSummary = metrics.exportAndReset();

        System.out.println("\nResultado del control de capacidad:");

        resetSummary.lines()
                .filter(line -> line.startsWith("métricas descartadas"))
                .forEach(System.out::println);

        // Comprobamos que las métricas se hayan limpiado
        System.out.println(
                "\nDespués del reinicio:\n" + metrics.export()
        );
    }
}