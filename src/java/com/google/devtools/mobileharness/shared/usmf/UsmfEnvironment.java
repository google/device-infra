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
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit rule to manage USMF sandbox environments for unit tests.
 *
 * <p>On test completion, this rule aggregates all captured command invocations from the sandbox
 * directory into a single {@code summary.json} file. In testing environments where undeclared
 * outputs are configured, this summary file will be uploaded as part of the test outputs.
 */
public final class UsmfEnvironment extends TestWatcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SUMMARY_FILE_NAME = "summary.json";

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Description currentDescription;
  private boolean isUsingTemporaryFolder;
  private Path sandboxParent;

  private final List<UsmfBinary> registeredBinaries = new ArrayList<>();
  private int sandboxCounter;

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
    return UsmfBinary.builder(binaryName, sandboxParent, sandboxName)
        .setBuildCallback(registeredBinaries::add);
  }

  @Override
  protected void starting(Description description) {
    // Clear previously registered binaries.
    this.registeredBinaries.clear();

    // Determine the sandbox base path. Use the undeclared outputs dir if exists,
    // otherwise fallback to a newly created local temporary folder.
    String undeclaredOutputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (undeclaredOutputsDir != null) {
      this.sandboxParent = Path.of(undeclaredOutputsDir);
    } else {
      try {
        temporaryFolder.create();
        this.isUsingTemporaryFolder = true;
        this.sandboxParent = temporaryFolder.getRoot().toPath();
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
          ImmutableList<UsmfBinary.CommandInvocation> invocations = binary.readCommandInvocations();
          Files.writeString(summaryFile, gson.toJson(invocations));
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "UsmfEnvironment failed to write summary.json for %s", binary.getPath());
        }
      }
    } finally {
      // Purge the temporary folder if one was created during starting.
      if (isUsingTemporaryFolder) {
        temporaryFolder.delete();
        isUsingTemporaryFolder = false;
      }
    }
  }
}
