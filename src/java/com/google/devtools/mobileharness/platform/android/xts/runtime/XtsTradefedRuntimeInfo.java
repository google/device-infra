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
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** A snapshot of xTS Tradefed runtime information. */
@AutoValue
public abstract class XtsTradefedRuntimeInfo {

  private static final XtsTradefedRuntimeInfo DEFAULT_INSTANCE =
      XtsTradefedRuntimeInfo.of(ImmutableList.of(), Instant.EPOCH);

  private static final String SEPARATOR = "\n";
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR);

  /** Invocations. Empty indicates that the invocation information is unknown. */
  public abstract ImmutableList<TradefedInvocation> invocations();

  /** Updated timestamp. */
  public abstract Instant timestamp();

  public static XtsTradefedRuntimeInfo getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  /** Returns a string that can be parsed by {@link #decodeFromString(String)}. */
  public String encodeToString() {
    return timestamp().toEpochMilli()
        + SEPARATOR
        + invocations().stream()
            .map(invocation -> encodeToBase64(invocation.encodeToString()))
            .collect(joining(SEPARATOR));
  }

  public static XtsTradefedRuntimeInfo decodeFromString(String string) {
    List<String> parts = SPLITTER.splitToList(string);
    Instant timestamp = Instant.ofEpochMilli(Long.parseLong(parts.get(0)));
    ImmutableList.Builder<TradefedInvocation> invocations = ImmutableList.builder();
    for (int i = 1; i < parts.size(); i++) {
      if (!parts.get(i).isEmpty()) {
        invocations.add(TradefedInvocation.decodeFromString(decodeFromBase64(parts.get(i))));
      }
    }

    return XtsTradefedRuntimeInfo.of(invocations.build(), timestamp);
  }

  public static XtsTradefedRuntimeInfo of(
      ImmutableList<TradefedInvocation> invocations, Instant timestamp) {
    return new AutoValue_XtsTradefedRuntimeInfo(invocations, timestamp);
  }

  private static String encodeToBase64(String s) {
    return Base64.getEncoder().encodeToString(s.getBytes(UTF_8));
  }

  private static String decodeFromBase64(String s) {
    return new String(Base64.getDecoder().decode(s), UTF_8);
  }

  /** Details of a Tradefed invocation. */
  @AutoValue
  public abstract static class TradefedInvocation {
    private static final TradefedInvocation DEFAULT_INSTANCE =
        TradefedInvocation.of(ImmutableList.of(), "");
    private static final String SEPARATOR = ",";
    private static final Splitter SPLITTER = Splitter.on(SEPARATOR);

    public abstract ImmutableList<String> deviceIds();

    public abstract String status();

    public static TradefedInvocation getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    /** Returns a string that can be parsed by {@link #decodeFromString(String)}. */
    public String encodeToString() {
      return encodeToBase64(status()).concat(SEPARATOR + String.join(SEPARATOR, deviceIds()));
    }

    /** Parses a string into a {@link TradefedInvocation}. */
    public static TradefedInvocation decodeFromString(String string) {
      List<String> parts = SPLITTER.splitToList(string);
      String status = decodeFromBase64(parts.get(0));
      ImmutableList.Builder<String> deviceIds = ImmutableList.builder();
      for (int i = 1; i < parts.size(); i++) {
        if (!parts.get(i).isEmpty()) {
          deviceIds.add(parts.get(i));
        }
      }
      return TradefedInvocation.of(deviceIds.build(), status);
    }

    public static TradefedInvocation of(ImmutableList<String> deviceIds, String status) {
      return new AutoValue_XtsTradefedRuntimeInfo_TradefedInvocation(deviceIds, status);
    }
  }
}
