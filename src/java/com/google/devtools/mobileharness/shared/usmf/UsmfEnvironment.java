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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit rule to manage USMF sandbox environments for unit tests.
 *
 * <p>This rule automatically resolves and configures the parent directory for sandbox deployment in
 * the following order of priority:
 *
 * <ul>
 *   <li>The explicitly specified path supplied via constructor.
 *   <li>The testing environment's undeclared outputs directory, which enables sandboxed files and
 *       command mock invocation logs to be uploaded automatically as part of the test outputs.
 *   <li>A fallback local temporary folder for local execution.
 * </ul>
 *
 * <p>Upon test completion, the rule aggregates all captured command invocations from the sandbox
 * directory into a single {@code summary.json} file. However, if a fallback local temporary folder
 * is used, this summary generation and writing is skipped since the temporary folder is purged on
 * completion.
 */
public final class UsmfEnvironment extends TestWatcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SUMMARY_FILE_NAME = "summary.json";
  private static final String ENV_UNDECLARED_OUTPUTS_DIR = "TEST_UNDECLARED_OUTPUTS_DIR";

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Nullable private final Path sandboxParentDir;
  private final boolean isUsingTemporaryFolder;

  private Description currentDescription;

  private final List<UsmfBinary> registeredBinaries = new ArrayList<>();
  private int sandboxCounter;

  /** Creates a {@link UsmfEnvironment} with default sandbox path auto-detection. */
  public UsmfEnvironment() {
    this(
        System.getenv(ENV_UNDECLARED_OUTPUTS_DIR) != null
            ? Path.of(System.getenv(ENV_UNDECLARED_OUTPUTS_DIR))
            : null);
  }

  /**
   * Creates a {@link UsmfEnvironment} with a custom parent directory for sandbox deployment.
   *
   * @param customSandboxParent the sandbox parent directory path, or {@code null} to force using a
   *     local temporary folder
   */
  public UsmfEnvironment(@Nullable Path customSandboxParent) {
    if (customSandboxParent != null) {
      this.sandboxParentDir = customSandboxParent;
      this.isUsingTemporaryFolder = false;
    } else {
      this.sandboxParentDir = null;
      this.isUsingTemporaryFolder = true;
    }
  }

  /**
   * Creates a {@link UsmfBinary.Builder} with sandbox directory automatically located, supporting
   * command invocation log aggregation and upload on test completion.
   *
   * @param binaryName the target command mock executor binary name, e.g., "adb"
   */
  public UsmfBinary.Builder createBinary(String binaryName) {
    // Ensure the rule starting lifecycle has completed.
    checkState(currentDescription != null, "UsmfEnvironment rule has not been initialized yet.");

    // Generate a unique sandbox directory name scoped to the class name, method name and counter.
    String sandboxName =
        "usmf_sandbox_"
            + binaryName
            + "_"
            + currentDescription.getTestClass().getSimpleName()
            + "_"
            + currentDescription.getMethodName()
            + "_"
            + sandboxCounter++;

    // Create a new binary builder and configure it to register the constructed binary upon build.
    Path parent = sandboxParentDir != null ? sandboxParentDir : temporaryFolder.getRoot().toPath();
    return UsmfBinary.builder(binaryName, parent, sandboxName)
        .setBuildCallback(registeredBinaries::add);
  }

  @Override
  protected void starting(Description description) {
    // Clear previously registered binaries.
    this.registeredBinaries.clear();

    if (isUsingTemporaryFolder) {
      try {
        temporaryFolder.create();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to create temporary folder", e);
      }
    }

    // Set description at the very end to guarantee safe publication of fields.
    this.currentDescription = description;
  }

  @Override
  protected void finished(Description description) {
    try {
      if (!isUsingTemporaryFolder) {
        Gson gson = new Gson();
        // Aggregate logs for all registered binaries.
        for (UsmfBinary binary : registeredBinaries) {
          try {
            // Skip sandbox log dirs that were never deployed.
            Path logsDir = Path.of(binary.getSandboxDir()).resolve(UsmfBinary.LOGS_DIR_NAME);
            if (!Files.exists(logsDir)) {
              continue;
            }

            // Read all invocations and write them to the final summary file.
            Path summaryFile = logsDir.resolve(SUMMARY_FILE_NAME);
            ImmutableList<UsmfBinary.CommandInvocation> invocations =
                binary.readCommandInvocations();
            Files.writeString(summaryFile, gson.toJson(invocations));
          } catch (IOException e) {
            logger.atWarning().withCause(e).log(
                "UsmfEnvironment failed to write summary.json for %s", binary.getPath());
          }
        }
      }
    } finally {
      // Purge the temporary folder if one was created during starting.
      if (isUsingTemporaryFolder) {
        temporaryFolder.delete();
      }
    }
  }
}
