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
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Represents a mocked command binary deployed within a hermetic sandbox directory.
 *
 * <p>A {@code UsmfBinary} intercepts CLI process invocations and delegates them to an embedded
 * execution stub that evaluates Python-like Starlark scripting rules.
 */
public final class UsmfBinary {

  private static final String BIN_DIR_NAME = "bin";
  static final String LOGS_DIR_NAME = "logs";
  private static final String RULES_DIR_NAME = "rules";
  private static final String STATE_DIR_NAME = "state";

  private static final String STUB_FILE_NAME = "usmf_stub";
  private static final String STUB_RESOURCE_PATH = "usmf_stub_/usmf_stub";
  private static final String VALIDATOR_FILE_NAME = "usmf_validator";
  private static final String VALIDATOR_RESOURCE_PATH = "usmf_validator_/usmf_validator";
  private static final String RULES_FILE_NAME = "rules.star";
  private static final String STATE_FILE_NAME = "state.json";
  private static final String HISTORY_FILE_PREFIX = "history_";
  private static final String JSON_FILE_EXTENSION = ".json";
  private static final String EMPTY_RULES_CONTENT = "usmf_rules = []\n";

  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private final Path binaryFile;
  private final Path sandboxDir;
  private final String rulesContent;
  private final Path logsDir;
  private final Path stateFile;

  private UsmfBinary(
      Path binaryFile, Path sandboxDir, String rulesContent, Path logsDir, Path stateFile) {
    this.binaryFile = binaryFile;
    this.sandboxDir = sandboxDir;
    this.rulesContent = rulesContent;
    this.logsDir = logsDir;
    this.stateFile = stateFile;
  }

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
   * @throws IllegalStateException if the configured sandbox directory already exists
   * @throws IOException if any I/O errors occur during file deployment or directory creation
   */
  public void deploy() throws IOException {
    checkState(!Files.exists(sandboxDir), "Sandbox directory already exists at [%s]", sandboxDir);
    Files.createDirectories(sandboxDir);

    Path binDir = sandboxDir.resolve(BIN_DIR_NAME);
    Path logsDir = sandboxDir.resolve(LOGS_DIR_NAME);
    Path rulesDir = sandboxDir.resolve(RULES_DIR_NAME);
    Path stateDir = sandboxDir.resolve(STATE_DIR_NAME);

    Files.createDirectories(binDir);
    Files.createDirectories(logsDir);
    Files.createDirectories(rulesDir);
    Files.createDirectories(stateDir);

    // Initialize default empty state database.
    writeState(new JsonObject());

    // Write the Starlark rules configuration file.
    Path rulesFile = rulesDir.resolve(RULES_FILE_NAME);
    Files.writeString(rulesFile, rulesContent);

    // Deploy the Go execution stub.
    Path stubPath = binDir.resolve(STUB_FILE_NAME);
    try (InputStream inputStream =
        checkNotNull(
            UsmfBinary.class.getResourceAsStream(STUB_RESOURCE_PATH),
            "Resource %s not found in classpath",
            STUB_RESOURCE_PATH)) {
      Files.copy(inputStream, stubPath);
    }
    stubPath.toFile().setExecutable(true);

    // Deploy the Go validation stub.
    Path validatorPath = binDir.resolve(VALIDATOR_FILE_NAME);
    try (InputStream inputStream =
        checkNotNull(
            UsmfBinary.class.getResourceAsStream(VALIDATOR_RESOURCE_PATH),
            "Resource %s not found in classpath",
            VALIDATOR_RESOURCE_PATH)) {
      Files.copy(inputStream, validatorPath);
    }
    validatorPath.toFile().setExecutable(true);

    // Validate Starlark rules syntax.
    CommandExecutor commandExecutor = new CommandExecutor();
    try {
      CommandResult result =
          commandExecutor.exec(
              Command.of(
                      validatorPath.toAbsolutePath().toString(),
                      rulesFile.toAbsolutePath().toString())
                  .successExitCodes(0, 1));
      if (result.exitCode() == 1) {
        throw new IOException(
            "Failed to validate Starlark rules script: " + result.stdout().trim());
      }
    } catch (CommandException e) {
      throw new IOException("Failed to run rules validator", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Starlark rules validation interrupted", e);
    }

    // Write wrapper script.
    String shellWrapper =
        String.format(
            """
            #!/bin/bash
            export USMF_RULES_FILE="%s"
            export USMF_LOGS_DIR="%s"
            export USMF_STATES_FILE="%s"
            exec "%s" "$@"
            """,
            rulesFile.toAbsolutePath(),
            logsDir.toAbsolutePath(),
            stateFile.toAbsolutePath(),
            stubPath.toAbsolutePath());
    Files.writeString(binaryFile, shellWrapper);
    binaryFile.toFile().setExecutable(true);
  }

  /** Returns the absolute path of the generated mock binary executable wrapper. */
  public String getPath() {
    return binaryFile.toAbsolutePath().toString();
  }

  /** Returns the sandbox root directory path. */
  public String getSandboxDir() {
    return sandboxDir.toAbsolutePath().toString();
  }

  /**
   * Reads all command execution logs from this mock sandbox, sorted chronologically.
   *
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
   * Reads the active state database inside the mock sandbox workspace.
   *
   * @throws IOException if fails to read state from the file system
   */
  public JsonObject readState() throws IOException {
    return JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
  }

  /**
   * Overwrites the active state database inside the mock sandbox workspace.
   *
   * @throws IOException if fails to write state to the file system
   */
  public void writeState(JsonObject json) throws IOException {
    Path lockFile = stateFile.resolveSibling(stateFile.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
      Files.writeString(tempFile, gson.toJson(json));
      Files.move(
          tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
  }

  /** Builder class to configure {@link UsmfBinary} instances. */
  public static final class Builder {
    private final String binaryFileName;
    private final Path sandboxDirParentDir;
    private final String sandboxDirName;
    @Nullable private Path binaryFileParentDir;
    private String rulesContent = EMPTY_RULES_CONTENT;
    @Nullable private Consumer<UsmfBinary> buildCallback;

    private Builder(String binaryFileName, Path sandboxDirParentDir, String sandboxDirName) {
      this.binaryFileName = checkNotNull(binaryFileName);
      this.sandboxDirParentDir = checkNotNull(sandboxDirParentDir);
      this.sandboxDirName = checkNotNull(sandboxDirName);
    }

    /** Sets the Starlark inline rules script content. */
    @CanIgnoreReturnValue
    public Builder setRules(String content) {
      this.rulesContent = checkNotNull(content);
      return this;
    }

    /** Sets the Starlark rules script content by reading from a file path. */
    @CanIgnoreReturnValue
    public Builder setRules(Path filepath) throws IOException {
      this.rulesContent = Files.readString(checkNotNull(filepath));
      return this;
    }

    /** Overrides the parent directory of the generated mock binary executable wrapper. */
    @CanIgnoreReturnValue
    public Builder overrideBinaryFileParentDir(Path binaryFileParentDir) {
      this.binaryFileParentDir = checkNotNull(binaryFileParentDir);
      return this;
    }

    @CanIgnoreReturnValue
    Builder setBuildCallback(Consumer<UsmfBinary> buildCallback) {
      this.buildCallback = checkNotNull(buildCallback);
      return this;
    }

    /** Builds the configured {@link UsmfBinary} instance. */
    public UsmfBinary build() {
      Path sandboxDir = sandboxDirParentDir.resolve(sandboxDirName);
      Path binDir = sandboxDir.resolve(BIN_DIR_NAME);
      Path logsDir = sandboxDir.resolve(LOGS_DIR_NAME);
      Path stateDir = sandboxDir.resolve(STATE_DIR_NAME);
      Path stateFile = stateDir.resolve(STATE_FILE_NAME);

      Path targetDir = binaryFileParentDir != null ? binaryFileParentDir : binDir;
      Path binaryFile = targetDir.resolve(binaryFileName);

      UsmfBinary binary = new UsmfBinary(binaryFile, sandboxDir, rulesContent, logsDir, stateFile);
      if (buildCallback != null) {
        buildCallback.accept(binary);
      }
      return binary;
    }

    /** Builds and deploys the configured {@link UsmfBinary} instance in one step. */
    public UsmfBinary buildAndDeploy() throws IOException {
      UsmfBinary binary = build();
      binary.deploy();
      return binary;
    }
  }

  /** Represents an audit record of a captured command invocation on the mocked binary. */
  public static final class CommandInvocation {
    @SerializedName("args")
    private final List<String> args;

    @SerializedName("status")
    private final Status status;

    @SerializedName("start_time_ms")
    private final long startTimeMs;

    @SerializedName("rule_name")
    @Nullable
    private final String ruleName;

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
        @Nullable String ruleName,
        @Nullable Result result,
        List<String> errors) {
      this.args = args;
      this.status = status;
      this.startTimeMs = startTimeMs;
      this.ruleName = ruleName;
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

    public Optional<String> getRuleName() {
      return Optional.ofNullable(ruleName);
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

    /** Represents the execution outcome details of a finished mock command invocation. */
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
