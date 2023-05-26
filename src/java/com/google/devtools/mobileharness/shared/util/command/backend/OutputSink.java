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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.io.ByteSink;
import com.google.errorprone.annotations.Immutable;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** A sink to which a command writes its output. */
@AutoValue
@AutoValue.CopyAnnotations
@Immutable
@SuppressWarnings("Immutable") // ByteSink is not @Immutable
public abstract class OutputSink {
  /** The kind of sink to which command output will be written. */
  public enum Kind {
    /** Output to a file. */
    FILE,
    /** Output appended to a file. */
    FILE_APPEND,
    /** Output to stderr of the parent JVM process; usually equivalent to {@link System#err}. */
    JVM_ERR,
    /** Output to stdout of the parent JVM process; usually equivalent to {@link System#out}. */
    JVM_OUT,
    /**
     * Output to stderr of the command process, so it may be read from {@link
     * CommandProcess#stderrStream} and {@link CommandResult#stderrBytes()}.
     */
    PROCESS_ERR,
    /**
     * Output to stdout of the command process, so it may be read from {@link
     * CommandProcess#stdoutStream} and {@link CommandResult#stdoutBytes()}.
     */
    PROCESS_OUT,
    /** Output to an output stream. */
    STREAM;
  }

  private static final OutputSink TO_JVM_ERR = create(Kind.JVM_ERR, Optional.empty());
  private static final OutputSink TO_JVM_OUT = create(Kind.JVM_OUT, Optional.empty());
  private static final OutputSink TO_PROCESS_ERR = create(Kind.PROCESS_ERR, Optional.empty());
  private static final OutputSink TO_PROCESS_OUT = create(Kind.PROCESS_OUT, Optional.empty());

  /** Output to the specified file, overwriting the current file at the path, if any. */
  public static OutputSink toFile(Path file) {
    return create(Kind.FILE, Optional.of(file));
  }

  /** Output to the specified file, optionally appended and otherwise overwritten. */
  public static OutputSink toFileAppend(Path file) {
    return create(Kind.FILE_APPEND, Optional.of(file));
  }

  /** Output to stderr of the parent JVM process; usually equivalent to {@link System#err}. */
  public static OutputSink toJvmErr() {
    return TO_JVM_ERR;
  }

  /** Output to stdout of the parent JVM process; usually equivalent to {@link System#out}. */
  public static OutputSink toJvmOut() {
    return TO_JVM_OUT;
  }

  /**
   * Output to stderr of the command process, so it may be read from {@link
   * CommandProcess#stderrStream} and {@link CommandResult#stderrBytes()}.
   */
  public static OutputSink toProcessErr() {
    return TO_PROCESS_ERR;
  }

  /**
   * Output to stdout of the command process, so it may be read from {@link
   * CommandProcess#stdoutStream} and {@link CommandResult#stdoutBytes()}.
   */
  public static OutputSink toProcessOut() {
    return TO_PROCESS_OUT;
  }

  /** Output to an output stream supplied by the specified byte sink. */
  public static OutputSink toStream(ByteSink streamSupplier) {
    return create(Kind.STREAM, Optional.of(streamSupplier));
  }

  private static OutputSink create(Kind kind, Optional<Object> parameter) {
    return new AutoValue_OutputSink(kind, parameter);
  }

  OutputSink() {}

  /** The {@link Kind} of output target. */
  public abstract Kind kind();

  /**
   * The file to which output is written, if {@link #kind} is {@code FILE}.
   *
   * @throws IllegalStateException - if the output target is not a file
   */
  public final Path file() {
    checkState(
        kind().equals(Kind.FILE) || kind().equals(Kind.FILE_APPEND),
        "Sink is %s, not a file.",
        kind());
    return (Path) parameter().get();
  }

  /**
   * The byte sink to which output is written, if {@link #kind} is {@code STREAM}.
   *
   * @throws IllegalStateException - if the output target is not a stream
   */
  public final ByteSink streamSupplier() {
    checkState(kind().equals(Kind.STREAM), "Sink is %s, not a stream.", kind());
    return (ByteSink) parameter().get();
  }

  abstract Optional<Object> parameter();

  @Override
  public final String toString() {
    ToStringHelper helper = MoreObjects.toStringHelper(OutputSink.class).add("kind", kind());
    if (parameter().isPresent()) {
      helper.addValue(parameter().get());
    }
    return helper.toString();
  }

  /**
   * Opens a new {@link OutputStream} that writes to this output sink. No stream can be opened when
   * {@link #kind} is {@code PROCESS_OUT} or {@code PROCESS_ERR}.
   *
   * @throws IllegalStateException - if {@link #kind} is {@code PROCESS_OUT} or {@code PROCESS_ERR}.
   * @throws IOException - if an I/O error occurs in the process of opening the stream
   */
  final OutputStream openStream() throws IOException {
    switch (kind()) {
      case FILE:
        return Files.newOutputStream(file());
      case FILE_APPEND:
        return Files.newOutputStream(file(), StandardOpenOption.APPEND);
      case JVM_ERR:
        return new ForwardingOutputStream(new FileOutputStream(FileDescriptor.err)) {
          @Override
          public void close() {
            // Do not close the (global) file descriptor.
          }
        };
      case JVM_OUT:
        return new ForwardingOutputStream(new FileOutputStream(FileDescriptor.out)) {
          @Override
          public void close() {
            // Do not close the (global) file descriptor.
          }
        };
      case PROCESS_ERR:
      case PROCESS_OUT:
        throw new IllegalStateException(
            "No output stream available when writing to a process stream");
      case STREAM:
        return streamSupplier().openStream();
    }
    throw new AssertionError(kind());
  }
}
