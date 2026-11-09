package com.anvex.simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SimulatorConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public HttpClient defenderHttpClient(SimulatorProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService scenarioScheduler() {
        return Executors.newScheduledThreadPool(2, namedFactory("anvex-sim-scheduler"));
    }

    @Bean(destroyMethod = "shutdownNow")
    public java.util.concurrent.ExecutorService scenarioWorkers() {
        return Executors.newFixedThreadPool(8, namedFactory("anvex-sim-worker"));
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    static Duration noDuration() {
        return Duration.ZERO;
    }
}
