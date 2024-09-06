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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher;

import com.google.auto.value.AutoValue;
import java.time.Instant;

/**
 * Event of a file transferring execution, it's either an {@link ExecutionType#SEND} or {@link
 * ExecutionType#GET}.
 */
@AutoValue
public abstract class FileTransferEvent {

  /** Start time of the execution. */
  abstract Instant start();

  /** End time of the execution. */
  abstract Instant end();

  /** Type of the execution. */
  abstract ExecutionType type();

  /** Size of the file in execution. */
  abstract long fileSize();

  /** Whether the file is cached (so execution is actually skipped). */
  abstract boolean isCached();

  public static Builder builder() {
    return new AutoValue_FileTransferEvent.Builder();
  }

  /** Type of execution. */
  public enum ExecutionType {
    SEND,
    GET,
  }

  /** Builder of {@link FileTransferEvent}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets starts time. */
    public abstract Builder setStart(Instant start);

    /** Sets end time. */
    public abstract Builder setEnd(Instant end);

    /** Sets execution type. */
    public abstract Builder setType(ExecutionType type);

    /** Sets size of the file. */
    public abstract Builder setFileSize(long fileSize);

    /** Sets whether file is cached. */
    public abstract Builder setIsCached(boolean isCached);

    /** Builds a {@link FileTransferEvent}, all fields must be set in advance. */
    public abstract FileTransferEvent build();
  }
}
