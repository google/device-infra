/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.usmf;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagsManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A manual command-line benchmark tool to measure process-level execution latency of USMF mock
 * binaries.
 *
 * <p>Usage:
 *
 * <pre>
 *   blaze run //third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness/shared/usmf:UsmfBenchmark -- --usmf_benchmark_iterations=50
 *   bazelisk run //src/javatests/com/google/devtools/mobileharness/shared/usmf:UsmfBenchmark -- --usmf_benchmark_iterations=50
 * </pre>
 */
public final class UsmfBenchmark {

  public static void main(String[] args) throws Exception {
    // 1. Parse standard MobileHarness global flags
    FlagsManager.parse(args);
    int iterations = Flags.usmfBenchmarkIterations.getNonNull();

    // 2. Prepare manual temporary folder sandbox
    Path tempFolder = Files.createTempDirectory("usmf_benchmark_sandbox");
    try {
      System.out.printf(
          "Deploying mock binary to temporary sandbox: %s%n", tempFolder.toAbsolutePath());

      UsmfBinary mockBin =
          UsmfBinary.builder("mock_tool", tempFolder, "benchmark_sandbox").buildAndDeploy();

      CommandExecutor executor = new CommandExecutor();
      List<Double> latencies = new ArrayList<>();

      // Warm up
      for (int i = 0; i < 5; i++) {
        executor.exec(Command.of(mockBin.getPath(), "warmup"));
      }

      System.out.printf(
          "Starting performance benchmark: running mock binary %d times...%n", iterations);

      long totalStart = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        long start = System.nanoTime();
        CommandResult result = executor.exec(Command.of(mockBin.getPath(), "ping"));
        double elapsed = (System.nanoTime() - start) / 1000000.0;
        latencies.add(elapsed);
        if (result.exitCode() != 0) {
          throw new IOException(
              String.format(
                  "Mock binary execution failed in round %d with exit code: %d",
                  i, result.exitCode()));
        }
      }
      double totalElapsed = (System.nanoTime() - totalStart) / 1000000.0;

      // 3. Compute distribution metrics
      Collections.sort(latencies);
      double min = latencies.get(0);
      double max = latencies.get(iterations - 1);
      double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
      double p50 = latencies.get((int) (iterations * 0.5));
      double p90 = latencies.get((int) (iterations * 0.9));
      double p95 = latencies.get((int) (iterations * 0.95));
      double p99 = latencies.get(iterations - 1);

      // 4. Output the metrics in a clean table format directly to stdout
      System.out.printf(
          """
          ==================================================
          === USMF STUB BENCHMARK PERFORMANCE RESULTS ===
          ==================================================
          Target Executable   : %s
          Total Iterations    : %d
          Total Elapsed Time  : %.2f ms (%.2f s)
          --------------------------------------------------
          Min Latency         : %.2f ms
          Max Latency         : %.2f ms
          Average Latency     : %.2f ms
          --------------------------------------------------
          Latency Percentiles:
            p50 (Median)      : %.2f ms
            p90               : %.2f ms
            p95               : %.2f ms
            p99 (Peak Outlier): %.2f ms
          ==================================================
          """,
          mockBin.getPath(),
          iterations,
          totalElapsed,
          totalElapsed / 1000.0,
          min,
          max,
          avg,
          p50,
          p90,
          p95,
          p99);

    } finally {
      // 5. Hard clean up temporary folder recursively to prevent disk leakage
      try {
        MoreFiles.deleteRecursively(tempFolder, RecursiveDeleteOption.ALLOW_INSECURE);
      } catch (IOException e) {
        System.err.printf(
            "Failed to clean up sandbox folder '%s': %s%n", tempFolder, e.getMessage());
      }
    }
  }

  private UsmfBenchmark() {}
}
