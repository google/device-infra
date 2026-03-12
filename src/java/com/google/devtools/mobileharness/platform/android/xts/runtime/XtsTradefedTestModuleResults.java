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
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Contains Tradefed test module execution progress. */
public class XtsTradefedTestModuleResults {

  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(
              Duration.class,
              new TypeAdapter<Duration>() {
                @Override
                public void write(JsonWriter out, Duration value) throws IOException {
                  out.value(value.toMillis());
                }

                @Override
                public Duration read(JsonReader in) throws IOException {
                  return Duration.ofMillis(in.nextLong());
                }
              })
          .create();

  public static XtsTradefedTestModuleResults decodeFromString(String string) {
    return GSON.fromJson(string, XtsTradefedTestModuleResults.class);
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
    return GSON.toJson(this);
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
}
