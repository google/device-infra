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
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.Immutable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A source from which a command reads its input. */
@AutoValue
@AutoValue.CopyAnnotations
@Immutable
@SuppressWarnings("Immutable") // ByteSource is not @Immutable
public abstract class InputSource {
  /** The kind of source from which command input will be read. */
  public enum Kind {
    /** Input from a file. */
    FILE,
    /** Input from the parent JVM process; usually equivalent to {@link System#in}. */
    JVM,
    /**
     * Input from the command process, so it may be written to {@link CommandProcess#stdinStream}.
     */
    PROCESS,
    /** Input from an input stream. */
    STREAM
  }

  private static final InputSource FROM_JVM = create(Kind.JVM, Optional.empty());
  private static final InputSource FROM_PROCESS = create(Kind.PROCESS, Optional.empty());

  /** Input from the specified file. */
  public static InputSource fromFile(Path file) {
    return create(Kind.FILE, Optional.of(file));
  }

  /** Input from the parent JVM process; usually equivalent to {@link System#in}. */
  public static InputSource fromJvm() {
    return FROM_JVM;
  }

  /** Input from the command process, so it may be written to {@link CommandProcess#stdinStream}. */
  public static InputSource fromProcess() {
    return FROM_PROCESS;
  }

  /** Input from an input stream supplied by the specified byte source. */
  public static InputSource fromStream(ByteSource byteSource) {
    return create(Kind.STREAM, Optional.of(byteSource));
  }

  private static InputSource create(Kind kind, Optional<Object> parameter) {
    return new AutoValue_InputSource(kind, parameter);
  }

  InputSource() {}

  /** The {@link Kind} of input source. */
  public abstract Kind kind();

  /**
   * The file from which input is read, if {@link #kind} is {@code FILE}.
   *
   * @throws IllegalStateException - if the input source is not a file
   */
  public final Path file() {
    checkState(kind().equals(Kind.FILE), "Source is %s, not a file.", kind());
    return (Path) parameter().get();
  }

  /**
   * The byte source from which input is read, if {@link #kind} is {@code STREAM}.
   *
   * @throws IllegalStateException - if the input source is not a stream
   */
  public final ByteSource streamSupplier() {
    checkState(kind().equals(Kind.STREAM), "Source is %s, not a stream.", kind());
    return (ByteSource) parameter().get();
  }

  abstract Optional<Object> parameter();

  @Override
  public final String toString() {
    ToStringHelper helper = MoreObjects.toStringHelper(InputSource.class).add("kind", kind());
    if (parameter().isPresent()) {
      helper.addValue(parameter().get());
    }
    return helper.toString();
  }

  /**
   * Opens a new {@link InputStream} that reads from this input source. No stream can be opened when
   * the {@link #kind} is {@code PROCESS}.
   *
   * @throws IllegalStateException - if {@link #kind} is {@code PROCESS}.
   * @throws IOException - if an I/O error occurs in the process of opening the stream
   */
  final InputStream openStream() throws IOException {
    switch (kind()) {
      case FILE:
        return Files.newInputStream(file());
      case JVM:
        return new FilterInputStream(new FileInputStream(FileDescriptor.in)) {
          @Override
          public void close() {
            // Do not close the (global) file descriptor.
          }
        };
      case PROCESS:
        throw new IllegalStateException(
            "No input stream available when reading from a process stream");
      case STREAM:
        return streamSupplier().openStream();
    }
    throw new AssertionError(kind());
  }
}
