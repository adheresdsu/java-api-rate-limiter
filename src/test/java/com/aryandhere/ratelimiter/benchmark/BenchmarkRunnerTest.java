package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryandhere.ratelimiter.config.AlgorithmType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkRunnerTest {

    @Test
    void runsExactlyTheConfiguredNumberOfTrialsForEveryAlgorithm() throws InterruptedException {
        BenchmarkConfig config = new BenchmarkConfig(2, 10, 2, 5, 3);

        Map<AlgorithmType, List<TrialResult>> results = new BenchmarkRunner().run(config);

        assertEquals(3, results.size());
        for (AlgorithmType algorithm : AlgorithmType.values()) {
            assertEquals(config.trials(), results.get(algorithm).size());
        }
    }

    @Test
    void everyTrialAccountsForExactlyTheConfiguredOperationCount() throws InterruptedException {
        BenchmarkConfig config = new BenchmarkConfig(3, 20, 2, 0, 3);
        long expectedTotal = (long) config.clients() * config.operationsPerClient();

        Map<AlgorithmType, List<TrialResult>> results = new BenchmarkRunner().run(config);

        for (List<TrialResult> trials : results.values()) {
            for (TrialResult trial : trials) {
                assertEquals(expectedTotal, trial.totalOperations());
                assertEquals(expectedTotal, trial.accepted() + trial.rejected());
                assertEquals(expectedTotal, trial.latency().count());
            }
        }
    }

    @Test
    void synthConfigurationProducesBothAcceptedAndRejectedDecisions() throws InterruptedException {
        // operationsPerClient=10 -> synthetic limit=5 per client -> both accept and reject occur.
        BenchmarkConfig config = new BenchmarkConfig(2, 10, 2, 0, 3);

        Map<AlgorithmType, List<TrialResult>> results = new BenchmarkRunner().run(config);

        for (List<TrialResult> trials : results.values()) {
            for (TrialResult trial : trials) {
                assertTrue(trial.accepted() > 0, "expected some accepted decisions");
                assertTrue(trial.rejected() > 0, "expected some rejected decisions");
            }
        }
    }

    @Test
    void latencyPercentilesAreOrderedCorrectlyInEveryTrial() throws InterruptedException {
        BenchmarkConfig config = new BenchmarkConfig(2, 20, 2, 0, 3);

        Map<AlgorithmType, List<TrialResult>> results = new BenchmarkRunner().run(config);

        for (List<TrialResult> trials : results.values()) {
            for (TrialResult trial : trials) {
                assertTrue(trial.latency().minNanos() <= trial.latency().p50Nanos());
                assertTrue(trial.latency().p50Nanos() <= trial.latency().p95Nanos());
                assertTrue(trial.latency().p95Nanos() <= trial.latency().p99Nanos());
                assertTrue(trial.latency().p99Nanos() <= trial.latency().maxNanos());
            }
        }
    }

    @Test
    void noWorkerThreadsRemainAliveAfterBenchmarkCompletes() throws InterruptedException {
        BenchmarkConfig config = new BenchmarkConfig(2, 10, 2, 5, 3);

        new BenchmarkRunner().run(config);

        long aliveWorkerThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(BenchmarkRunner.WORKER_THREAD_NAME_PREFIX))
                .filter(Thread::isAlive)
                .count();
        assertEquals(0, aliveWorkerThreads);
    }
}
