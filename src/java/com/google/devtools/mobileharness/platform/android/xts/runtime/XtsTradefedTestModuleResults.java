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
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Contains Tradefed test module execution progress. */
public class XtsTradefedTestModuleResults {

  private static final String LINE_SEPARATOR = "\n";
  private static final String TOKEN_SEPARATOR = ";";
  private static final String KEY_VALUE_SEPARATOR = ":";

  @SuppressWarnings(
      "JdkCollectors") // To avoid pulling in the Guava dep (for the Nullable annotation) to keep
  // the size of the agent jar small.
  public static XtsTradefedTestModuleResults decodeFromString(String string) {
    List<String> lines = split(string, LINE_SEPARATOR);
    Map<String, List<ModuleInfo>> map = new HashMap<>();

    for (String line : lines) {
      if (line.isEmpty()) {
        continue;
      }
      List<String> parts = split(line, KEY_VALUE_SEPARATOR);
      if (parts.size() < 2) {
        continue; // Invalid line format
      }
      String uniqueId = decodeFromBase64(parts.get(0));
      List<String> moduleStrings = split(parts.get(1), TOKEN_SEPARATOR);

      List<ModuleInfo> modules =
          moduleStrings.stream()
              .filter(s -> !s.isEmpty())
              .map(ModuleInfo::decodeFromString)
              .collect(toList());

      map.put(uniqueId, Collections.unmodifiableList(modules));
    }
    return new XtsTradefedTestModuleResults(map);
  }

  // invocationId -> List<ModuleInfo>
  private final Map<String, List<ModuleInfo>> runningModules;

  public XtsTradefedTestModuleResults(Map<String, List<ModuleInfo>> runningModules) {
    this.runningModules = Collections.unmodifiableMap(new HashMap<>(runningModules));
  }

  /** Gets the mapping of running modules per Tradefed invocation ID. */
  public Map<String, List<ModuleInfo>> runningModules() {
    return runningModules;
  }

  public String encodeToString() {
    return runningModules.entrySet().stream()
        .map(
            entry -> {
              String modulesStr =
                  entry.getValue().stream()
                      .map(ModuleInfo::encodeToString)
                      .collect(joining(TOKEN_SEPARATOR));
              return encodeToBase64(entry.getKey()) + KEY_VALUE_SEPARATOR + modulesStr;
            })
        .collect(joining(LINE_SEPARATOR));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XtsTradefedTestModuleResults that)) {
      return false;
    }
    return Objects.equals(runningModules, that.runningModules);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(runningModules);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("XtsTradefedTestModuleResults{\n");
    runningModules.forEach(
        (invocationId, modules) -> {
          sb.append(String.format("  invocationId=%s:\n", invocationId));
          for (ModuleInfo module : modules) {
            sb.append("    ").append(module).append("\n");
          }
        });
    sb.append("}");
    return sb.toString();
  }

  /** Details of a Tradefed module. */
  public static class ModuleInfo {

    private static final String MODULE_TOKEN_SEPARATOR = ";";

    public static ModuleInfo decodeFromString(String string) {
      String decoded = decodeFromBase64(string);
      List<String> parts = split(decoded, MODULE_TOKEN_SEPARATOR);
      String id = decodeFromBase64(parts.get(0));
      boolean isRunning = Boolean.parseBoolean(parts.get(1));
      int testsExpected = parts.size() > 2 ? Integer.parseInt(parts.get(2)) : 0;
      int testsCompleted = parts.size() > 3 ? Integer.parseInt(parts.get(3)) : 0;
      int testsFailed = parts.size() > 4 ? Integer.parseInt(parts.get(4)) : 0;
      int testsPassed = parts.size() > 5 ? Integer.parseInt(parts.get(5)) : 0;
      int testsSkipped = parts.size() > 6 ? Integer.parseInt(parts.get(6)) : 0;
      long durationMillis = parts.size() > 7 ? Long.parseLong(parts.get(7)) : 0L;
      return new ModuleInfo(
          id,
          isRunning,
          testsExpected,
          testsCompleted,
          testsFailed,
          testsPassed,
          testsSkipped,
          Duration.ofMillis(durationMillis));
    }

    private final String id;
    private final boolean isRunning;
    private final int testsExpected;
    private final int testsCompleted;
    private final int testsFailed;
    private final int testsPassed;
    private final int testsSkipped;
    private final Duration duration;

    public ModuleInfo(
        String id,
        boolean isRunning,
        int testsExpected,
        int testsCompleted,
        int testsFailed,
        int testsPassed,
        int testsSkipped,
        Duration duration) {
      this.id = id;
      this.isRunning = isRunning;
      this.testsExpected = testsExpected;
      this.testsCompleted = testsCompleted;
      this.testsFailed = testsFailed;
      this.testsPassed = testsPassed;
      this.testsSkipped = testsSkipped;
      this.duration = duration;
    }

    public ModuleInfo(String id, boolean isRunning) {
      this(id, isRunning, 0, 0, 0, 0, 0, Duration.ZERO);
    }

    public String id() {
      return id;
    }

    public boolean isRunning() {
      return isRunning;
    }

    public int testsExpected() {
      return testsExpected;
    }

    public int testsCompleted() {
      return testsCompleted;
    }

    public int testsFailed() {
      return testsFailed;
    }

    public int testsPassed() {
      return testsPassed;
    }

    public int testsSkipped() {
      return testsSkipped;
    }

    public Duration duration() {
      return duration;
    }

    public String encodeToString() {
      String joined =
          encodeToBase64(id())
              + MODULE_TOKEN_SEPARATOR
              + isRunning()
              + MODULE_TOKEN_SEPARATOR
              + testsExpected()
              + MODULE_TOKEN_SEPARATOR
              + testsCompleted()
              + MODULE_TOKEN_SEPARATOR
              + testsFailed()
              + MODULE_TOKEN_SEPARATOR
              + testsPassed()
              + MODULE_TOKEN_SEPARATOR
              + testsSkipped()
              + MODULE_TOKEN_SEPARATOR
              + duration().toMillis();
      return encodeToBase64(joined);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ModuleInfo that)) {
        return false;
      }
      return isRunning == that.isRunning
          && testsExpected == that.testsExpected
          && testsCompleted == that.testsCompleted
          && testsFailed == that.testsFailed
          && testsPassed == that.testsPassed
          && testsSkipped == that.testsSkipped
          && Objects.equals(duration, that.duration)
          && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          id,
          isRunning,
          testsExpected,
          testsCompleted,
          testsFailed,
          testsPassed,
          testsSkipped,
          duration);
    }

    @Override
    public String toString() {
      return String.format(
          "ModuleInfo{id='%s', state=%s, tests: [expected=%d, completed=%d, passed=%d, failed=%d,"
              + " skipped=%d], duration=%s}",
          id,
          isRunning ? "RUNNING" : "DONE",
          testsExpected,
          testsCompleted,
          testsPassed,
          testsFailed,
          testsSkipped,
          duration);
    }
  }

  private static String encodeToBase64(String string) {
    return Base64.getEncoder().encodeToString(string.getBytes(UTF_8));
  }

  private static String decodeFromBase64(String string) {
    return new String(Base64.getDecoder().decode(string), UTF_8);
  }

  private static List<String> split(String string, String separator) {
    // -1 limit to match Guava Splitter's behavior of preserving trailing empty strings
    return Arrays.asList(string.split(separator, /* limit= */ -1));
  }
}
