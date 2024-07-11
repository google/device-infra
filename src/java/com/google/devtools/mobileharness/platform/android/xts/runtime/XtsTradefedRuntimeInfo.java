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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** A snapshot of xTS Tradefed runtime information. */
public class XtsTradefedRuntimeInfo {

  private static final XtsTradefedRuntimeInfo DEFAULT_INSTANCE =
      new XtsTradefedRuntimeInfo(new ArrayList<>(), Instant.EPOCH);

  public static XtsTradefedRuntimeInfo getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  public static XtsTradefedRuntimeInfo decodeFromString(String string) {
    List<String> parts = split(string, LINE_SEPARATOR);
    Instant timestamp = Instant.ofEpochMilli(Long.parseLong(parts.get(0)));
    List<TradefedInvocation> invocations = new ArrayList<>();
    for (int i = 1; i < parts.size(); i++) {
      if (!parts.get(i).isEmpty()) {
        invocations.add(TradefedInvocation.decodeFromString(decodeFromBase64(parts.get(i))));
      }
    }

    return new XtsTradefedRuntimeInfo(invocations, timestamp);
  }

  private static final String LINE_SEPARATOR = "\n";
  private static final String TOKEN_SEPARATOR = ",";

  private final List<TradefedInvocation> invocations;
  private final Instant timestamp;

  public XtsTradefedRuntimeInfo(List<TradefedInvocation> invocations, Instant timestamp) {
    this.invocations = new ArrayList<>(invocations);
    this.timestamp = timestamp;
  }

  /**
   * Invocations.
   *
   * <p>Empty indicates that the invocation information is unknown.
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
    return timestamp().toEpochMilli()
        + LINE_SEPARATOR
        + invocations().stream()
            .map(invocation -> encodeToBase64(invocation.encodeToString()))
            .collect(joining(LINE_SEPARATOR));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XtsTradefedRuntimeInfo)) {
      return false;
    }
    XtsTradefedRuntimeInfo that = (XtsTradefedRuntimeInfo) o;
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
        new TradefedInvocation(new ArrayList<>(), "");

    public static TradefedInvocation getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    /** Parses a string into a {@link TradefedInvocation}. */
    public static TradefedInvocation decodeFromString(String string) {
      List<String> parts = split(string, TOKEN_SEPARATOR);
      String status = decodeFromBase64(parts.get(0));
      List<String> deviceIds = new ArrayList<>();
      for (int i = 1; i < parts.size(); i++) {
        if (!parts.get(i).isEmpty()) {
          deviceIds.add(parts.get(i));
        }
      }

      return new TradefedInvocation(deviceIds, status);
    }

    private final List<String> deviceIds;
    private final String status;

    public TradefedInvocation(List<String> deviceIds, String status) {
      this.deviceIds = new ArrayList<>(deviceIds);
      this.status = status;
    }

    public List<String> deviceIds() {
      return deviceIds;
    }

    public String status() {
      return status;
    }

    /** Returns a string that can be parsed by {@link #decodeFromString(String)}. */
    public String encodeToString() {
      return encodeToBase64(status())
          .concat(TOKEN_SEPARATOR + String.join(TOKEN_SEPARATOR, deviceIds()));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TradefedInvocation)) {
        return false;
      }
      TradefedInvocation that = (TradefedInvocation) o;
      return Objects.equals(deviceIds, that.deviceIds) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
      return Objects.hash(deviceIds, status);
    }

    @Override
    public String toString() {
      return "TradefedInvocation{deviceIds=" + deviceIds + ", status='" + status + "'}";
    }
  }

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  private static List<String> split(String string, String separator) {
    String[] result = string.split(separator);
    return result.length == 0 ? asList("") : asList(result);
  }

  private static String encodeToBase64(String string) {
    return Base64.getEncoder().encodeToString(string.getBytes(UTF_8));
  }

  private static String decodeFromBase64(String string) {
    return new String(Base64.getDecoder().decode(string), UTF_8);
  }
}
