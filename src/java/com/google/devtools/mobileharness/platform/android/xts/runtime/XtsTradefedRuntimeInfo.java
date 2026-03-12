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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.Gson;
import com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.GsonBuilder;
import com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.TypeAdapter;
import com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.stream.JsonReader;
import com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A snapshot of xTS Tradefed runtime information. */
public class XtsTradefedRuntimeInfo {

  private static final XtsTradefedRuntimeInfo DEFAULT_INSTANCE =
      new XtsTradefedRuntimeInfo(new ArrayList<>(), Instant.EPOCH);

  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(
              Instant.class,
              new TypeAdapter<Instant>() {
                @Override
                public void write(JsonWriter out, Instant value) throws IOException {
                  out.value(value.toEpochMilli());
                }

                @Override
                public Instant read(JsonReader in) throws IOException {
                  return Instant.ofEpochMilli(in.nextLong());
                }
              })
          .create();

  public static XtsTradefedRuntimeInfo getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  public static XtsTradefedRuntimeInfo decodeFromString(String string) {
    return GSON.fromJson(string, XtsTradefedRuntimeInfo.class);
  }

  private final List<TradefedInvocation> invocations;
  private final Instant timestamp;

  public XtsTradefedRuntimeInfo(List<TradefedInvocation> invocations, Instant timestamp) {
    this.invocations = new ArrayList<>(invocations);
    this.timestamp = timestamp;
  }

  /**
   * A list of currently running invocations (isRunning=true) or invocations with errors
   * (isRunning=false). Completed invocations are not included.
   */
  public List<TradefedInvocation> invocations() {
    return invocations;
  }

  /** Updated timestamp. */
  public Instant timestamp() {
    return timestamp;
  }

  /** Returns a string that can be parsed by {@link #decodeFromString(String)}. */
  public String encodeToString() {
    return GSON.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XtsTradefedRuntimeInfo that)) {
      return false;
    }
    return Objects.equals(invocations, that.invocations)
        && Objects.equals(timestamp, that.timestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invocations, timestamp);
  }

  @Override
  public String toString() {
    return "XtsTradefedRuntimeInfo{invocations=" + invocations + ", timestamp=" + timestamp + "}";
  }

  /** Details of a Tradefed invocation. */
  public static class TradefedInvocation {

    private static final TradefedInvocation DEFAULT_INSTANCE =
        new TradefedInvocation(
            /* isRunning= */ false,
            /* deviceIds= */ new ArrayList<>(),
            /* status= */ "",
            /* errorMessage= */ "");

    public static TradefedInvocation getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    public static TradefedInvocation decodeFromString(String string) {
      return GSON.fromJson(string, TradefedInvocation.class);
    }

    public String encodeToString() {
      return GSON.toJson(this);
    }

    // Indicates whether it's a currently running invocation.
    private final boolean isRunning;
    private final List<String> deviceIds;
    private final String status;
    private final String errorMessage;

    public TradefedInvocation(
        boolean isRunning, List<String> deviceIds, String status, String errorMessage) {
      this.isRunning = isRunning;
      this.deviceIds = new ArrayList<>(deviceIds);
      this.status = status;
      this.errorMessage = errorMessage;
    }

    public boolean isRunning() {
      return isRunning;
    }

    public List<String> deviceIds() {
      return deviceIds;
    }

    public String status() {
      return status;
    }

    public String errorMessage() {
      return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TradefedInvocation that)) {
        return false;
      }
      return isRunning == that.isRunning
          && Objects.equals(deviceIds, that.deviceIds)
          && Objects.equals(status, that.status)
          && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
      return Objects.hash(isRunning, deviceIds, status, errorMessage);
    }

    @Override
    public String toString() {
      return String.format(
          "TradefedInvocation{isRunning='%s', deviceIds=%s, status='%s', errorMessage='%s'}",
          isRunning, deviceIds, status, errorMessage);
    }
  }
}
