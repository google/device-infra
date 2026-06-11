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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represents a mocked command binary (e.g., {@code adb}, {@code fastboot}) deployed within a
 * hermetic sandbox directory.
 *
 * <p>A {@code UsmfBinary} intercepts CLI process invocations and delegates them to an embedded
 * Python stub. Its core execution lifecycle operates as follows:
 *
 * <ul>
 *   <li><b>Rule Matching</b>: The stub evaluates incoming arguments and state variables
 *       sequentially against each configured {@link UsmfRule} in the order they were registered.
 *   <li><b>First-Match-Win</b>: The evaluation halts immediately at the first matching rule, which
 *       then executes its associated behavior (e.g., producing {@code stdout}/{@code stderr}
 *       streams, exit codes, state mutations, and host side effects).
 *   <li><b>Fallback Behavior</b>: If no rules match the command (or if zero rules are configured),
 *       the mock binary silently returns a successful exit code of {@code 0} with empty outputs,
 *       without forwarding the call to the real system binary.
 *   <li><b>Execution Auditing</b>: During execution, the mock binary logs running and completion
 *       audit records to log files containing trace details (argument parameters, exit code, and
 *       start/end timestamps). Applications can inspect these chronological {@link
 *       CommandInvocation} records to assert on invocation history at the end of tests.
 * </ul>
 *
 * <p>Instances are configured and deployed using {@link UsmfBinary.Builder}.
 *
 * @see UsmfRule
 */
public final class UsmfBinary {

  private static final String BIN_DIR_NAME = "bin";
  static final String LOGS_DIR_NAME = "logs";
  private static final String RULES_DIR_NAME = "rules";
  private static final String STATES_DIR_NAME = "states";

  private static final String STUB_FILE_NAME = "usmf_stub.py";
  private static final String RULES_FILE_NAME = "mock_rules.json";
  private static final String STATES_FILE_NAME = "states.json";
  private static final String HISTORY_FILE_PREFIX = "history_";
  private static final String JSON_FILE_EXTENSION = ".json";

  private final Path binaryFile;
  private final Path sandboxDir;
  private final ImmutableList<UsmfRule> rules;
  private final JsonObject variables;
  private final Path logsDir;
  private final Path statesFile;
  private final Gson gson = new Gson();

  /**
   * Creates a {@link Builder} to configure a mock binary sandbox.
   *
   * @param binaryFileName the filename of the target mock binary (e.g., "adb")
   * @param sandboxDirParentDir the parent directory where the sandbox will be created
   * @param sandboxDirName the name of the sandbox directory
   */
  public static Builder builder(
      String binaryFileName, Path sandboxDirParentDir, String sandboxDirName) {
    return new Builder(binaryFileName, sandboxDirParentDir, sandboxDirName);
  }

  /**
   * Deploys the mock binary sandbox environment and initializes all associated files.
   *
   * <p>This method creates the sandbox directories, writes configuration rules, deploys the Python
   * execution stub, and generates the wrapper shell script under the target executable path.
   *
   * @throws IllegalStateException if the configured sandbox directory already exists
   * @throws IOException if any I/O errors occur during file deployment or directory creation
   */
  public void deploy() throws IOException {
    // 1. Verify the sandbox directory is clean and create the sandbox directory structure.
    checkState(!Files.exists(sandboxDir), "Sandbox directory already exists at [%s]", sandboxDir);
    Files.createDirectories(sandboxDir);

    Path binDir = sandboxDir.resolve(BIN_DIR_NAME);
    Path logsDir = sandboxDir.resolve(LOGS_DIR_NAME);
    Path rulesDir = sandboxDir.resolve(RULES_DIR_NAME);
    Path statesDir = sandboxDir.resolve(STATES_DIR_NAME);

    Files.createDirectories(binDir);
    Files.createDirectories(logsDir);
    Files.createDirectories(rulesDir);
    Files.createDirectories(statesDir);

    // 2. Initialize the default empty state repository.
    writeStateJson(new JsonObject());

    // 3. Serialize rule configurations to JSON and save it under the rules directory.
    Path rulesFile = rulesDir.resolve(RULES_FILE_NAME);
    ImmutableMap<String, Object> config = ImmutableMap.of("rules", rules, "variables", variables);
    Files.writeString(rulesFile, gson.toJson(config));

    // 4. Write the Python execution stub into the bin directory and make it executable.
    Path stubPath = binDir.resolve(STUB_FILE_NAME);
    try (InputStream inputStream =
        checkNotNull(
            UsmfBinary.class.getResourceAsStream(STUB_FILE_NAME),
            "Resource %s not found in classpath",
            STUB_FILE_NAME)) {
      Files.copy(inputStream, stubPath);
    }
    stubPath.toFile().setExecutable(true);

    // 5. Write the wrapper script at the target binary path and make it executable.
    String execCommand = String.format("exec python3 \"%s\" \"$@\"", stubPath.toAbsolutePath());

    String shellWrapper =
        String.format(
            """
            #!/bin/bash
            export USMF_RULES_FILE="%s"
            export USMF_LOGS_DIR="%s"
            export USMF_STATES_FILE="%s"
            %s
            """,
            rulesFile.toAbsolutePath(),
            logsDir.toAbsolutePath(),
            statesFile.toAbsolutePath(),
            execCommand);
    Files.writeString(binaryFile, shellWrapper);
    binaryFile.toFile().setExecutable(true);
  }

  /**
   * Returns the absolute path of the generated mock binary executable wrapper.
   *
   * <p>Note that this is the path to the executable shell script wrapper that intercepts target CLI
   * commands, not the root path of the sandbox directory.
   */
  public String getPath() {
    return binaryFile.toAbsolutePath().toString();
  }

  /** Returns the sandbox root directory path. */
  public String getSandboxDir() {
    return sandboxDir.toAbsolutePath().toString();
  }

  /**
   * Reads the list of all captured command invocations on this mock sandbox, sorted chronologically
   * by their start time.
   *
   * @return the list of command invocations, sorted by start time
   * @throws IOException if fails to read logs from the file system
   */
  public ImmutableList<CommandInvocation> readCommandInvocations() throws IOException {
    try (Stream<Path> streamList = Files.list(logsDir)) {
      ImmutableList<Path> logFiles =
          streamList
              .filter(
                  path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith(HISTORY_FILE_PREFIX)
                        && fileName.endsWith(JSON_FILE_EXTENSION);
                  })
              .collect(toImmutableList());
      List<CommandInvocation> result = new ArrayList<>(logFiles.size());
      for (Path path : logFiles) {
        String content = Files.readString(path);
        CommandInvocation invocation = gson.fromJson(content, CommandInvocation.class);
        if (invocation != null) {
          result.add(invocation);
        }
      }
      result.sort(Comparator.comparing(CommandInvocation::getStartInstant));
      return ImmutableList.copyOf(result);
    }
  }

  /**
   * Reads the entire central state database JSON content inside the mock sandbox workspace.
   *
   * @return the raw JSON content of the active state database
   * @throws IOException if fails to read state from the file system
   */
  public JsonObject readStateJson() throws IOException {
    return JsonParser.parseString(Files.readString(statesFile)).getAsJsonObject();
  }

  /**
   * Overwrites the entire central state database JSON content inside the mock sandbox workspace.
   *
   * @param json the raw JSON content to overwrite in the active state database
   * @throws IOException if fails to write state to the file system
   */
  public void writeStateJson(JsonObject json) throws IOException {
    Path lockFile = statesFile.resolveSibling(statesFile.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      Path tempFile = statesFile.resolveSibling(statesFile.getFileName() + ".tmp");
      Files.writeString(tempFile, gson.toJson(json));
      Files.move(
          tempFile,
          statesFile,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    }
  }

  private UsmfBinary(
      Path binaryFile,
      Path sandboxDir,
      ImmutableList<UsmfRule> rules,
      JsonObject variables,
      Path logsDir,
      Path statesFile) {
    this.binaryFile = binaryFile;
    this.sandboxDir = sandboxDir;
    this.rules = rules;
    this.variables = variables;
    this.logsDir = logsDir;
    this.statesFile = statesFile;
  }

  /** Builder class to configure {@link UsmfBinary} instances. */
  public static final class Builder {
    private final String binaryFileName;
    private final Path sandboxDirParentDir;
    private final String sandboxDirName;
    @Nullable private Path binaryFileParentDir;
    private final List<UsmfRule> rules = new ArrayList<>();
    private JsonObject variables = new JsonObject();
    @Nullable private Consumer<UsmfBinary> buildCallback;

    private Builder(String binaryFileName, Path sandboxDirParentDir, String sandboxDirName) {
      this.binaryFileName = checkNotNull(binaryFileName);
      this.sandboxDirParentDir = checkNotNull(sandboxDirParentDir);
      this.sandboxDirName = checkNotNull(sandboxDirName);
    }

    @CanIgnoreReturnValue
    Builder setBuildCallback(Consumer<UsmfBinary> buildCallback) {
      this.buildCallback = checkNotNull(buildCallback);
      return this;
    }

    /**
     * Sets global mapping variables configuration.
     *
     * @param variables the predefined mappings of rule variables
     * @return this builder instance
     */
    @CanIgnoreReturnValue
    public Builder setVariables(JsonObject variables) {
      this.variables = checkNotNull(variables);
      return this;
    }

    /**
     * Adds an execution rule. Rules are evaluated sequentially at runtime in the order they are
     * added. The evaluation halts immediately at the first matching rule.
     *
     * @param rule the rule to be added
     * @return this builder instance
     */
    @CanIgnoreReturnValue
    public Builder addRule(UsmfRule rule) {
      this.rules.add(checkNotNull(rule));
      return this;
    }

    /**
     * Overrides the parent directory of the generated mock binary executable wrapper.
     *
     * <p>If this method is not called, the executable wrapper will be generated directly under the
     * sandbox's {@code bin} subdirectory by default. Overriding this can be useful when you need
     * the mock binary to be written to a specific external directory (e.g., a system path or a path
     * that matches other execution tools).
     *
     * @param binaryFileParentDir the custom parent directory for the mock binary wrapper
     * @return this builder instance
     */
    @CanIgnoreReturnValue
    public Builder overrideBinaryFileParentDir(Path binaryFileParentDir) {
      this.binaryFileParentDir = checkNotNull(binaryFileParentDir);
      return this;
    }

    public UsmfBinary build() {
      Path sandboxDir = sandboxDirParentDir.resolve(sandboxDirName);
      Path binDir = sandboxDir.resolve(BIN_DIR_NAME);
      Path logsDir = sandboxDir.resolve(LOGS_DIR_NAME);
      Path statesDir = sandboxDir.resolve(STATES_DIR_NAME);
      Path statesFile = statesDir.resolve(STATES_FILE_NAME);

      Path targetDir = binaryFileParentDir != null ? binaryFileParentDir : binDir;
      Path binaryFile = targetDir.resolve(binaryFileName);

      UsmfBinary binary =
          new UsmfBinary(
              binaryFile, sandboxDir, ImmutableList.copyOf(rules), variables, logsDir, statesFile);
      if (buildCallback != null) {
        buildCallback.accept(binary);
      }
      return binary;
    }

    /**
     * Builds and deploys the configured {@link UsmfBinary} instance in one step.
     *
     * <p>This is a helper method that initializes the {@code UsmfBinary} instance and immediately
     * invokes {@link UsmfBinary#deploy()} to draft all sandbox configurations and write the stub to
     * the file system.
     *
     * @return the deployed {@code UsmfBinary} instance
     * @throws IllegalStateException if the configured sandbox directory already exists
     * @throws IOException if any I/O errors occur during file deployment or directory creation
     * @see UsmfBinary#deploy()
     */
    public UsmfBinary buildAndDeploy() throws IOException {
      UsmfBinary binary = build();
      binary.deploy();
      return binary;
    }
  }

  /**
   * Represents an audit record of a captured command invocation on the mocked binary.
   *
   * <p>A {@code CommandInvocation} provides metadata about a process run, including its arguments,
   * start timestamp, and execution {@link Status} (either {@code RUNNING} or {@code FINISHED}).
   *
   * <p>If the invocation is finished, detailed outcome telemetry (such as the exit code,
   * stdout/stderr streams, and end timestamp) is nested and accessible via {@link #getResult()} or
   * {@link #getResultNonEmpty()}.
   *
   * <p>Any non-fatal error messages encountered during intercept execution (such as rules loading
   * failures or side-effect execution issues) are captured and accessible via {@link #getErrors()}.
   */
  public static final class CommandInvocation {
    @SerializedName("args")
    private final List<String> args;

    @SerializedName("status")
    private final Status status;

    @SerializedName("start_time_ms")
    private final long startTimeMs;

    @SerializedName("result")
    @Nullable
    private final Result result;

    @SerializedName("errors")
    private final List<String> errors;

    @SuppressWarnings("unused")
    private CommandInvocation(
        List<String> args,
        Status status,
        long startTimeMs,
        @Nullable Result result,
        List<String> errors) {
      this.args = args;
      this.status = status;
      this.startTimeMs = startTimeMs;
      this.result = result;
      this.errors = errors;
    }

    public ImmutableList<String> getArgs() {
      return args == null ? ImmutableList.of() : ImmutableList.copyOf(args);
    }

    public Status getStatus() {
      return status;
    }

    public Instant getStartInstant() {
      return Instant.ofEpochMilli(startTimeMs);
    }

    public Optional<Result> getResult() {
      return Optional.ofNullable(result);
    }

    public Result getResultNonEmpty() {
      return getResult().orElseThrow();
    }

    public ImmutableList<String> getErrors() {
      return errors == null ? ImmutableList.of() : ImmutableList.copyOf(errors);
    }

    /** Status of a command invocation. */
    public enum Status {
      RUNNING,
      FINISHED
    }

    /**
     * Represents the execution outcome details of a finished mock command invocation.
     *
     * <p>Once the command is completed (its status is {@link Status#FINISHED}), all outcome
     * properties (such as the exit code, stdout, stderr, and end timestamp) are guaranteed to be
     * fully populated and accessible.
     */
    public static final class Result {
      @SerializedName("exit_code")
      private final int exitCode;

      @SerializedName("stdout")
      private final String stdout;

      @SerializedName("stderr")
      private final String stderr;

      @SerializedName("end_time_ms")
      private final long endTimeMs;

      @SuppressWarnings("unused")
      private Result(int exitCode, String stdout, String stderr, long endTimeMs) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.endTimeMs = endTimeMs;
      }

      public int getExitCode() {
        return exitCode;
      }

      public String getStdout() {
        return stdout;
      }

      public String getStderr() {
        return stderr;
      }

      public Instant getEndInstant() {
        return Instant.ofEpochMilli(endTimeMs);
      }
    }
  }
}
