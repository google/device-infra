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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults.ModuleInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XtsTradefedTestModuleResultsMonitorTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  // Mock class to mimic Tradefed's MultiMap.
  public static class MockMultiMap<K, V> {
    private final Map<K, List<V>> map = new HashMap<>();

    public void put(K key, V value) {
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public List<V> get(K key) {
      return map.get(key);
    }
  }

  // Mock class to mimic Tradefed's IInvocationContext.
  public static class MockInvocationContext {
    private final MockMultiMap<String, String> attributes = new MockMultiMap<>();
    private final String invocationId;

    public MockInvocationContext(String invocationId) {
      this.invocationId = invocationId;
    }

    public String getInvocationId() {
      return invocationId;
    }

    public void addAttribute(String key, String value) {
      attributes.put(key, value);
    }

    public MockMultiMap<String, String> getAttributes() {
      return attributes;
    }
  }

  @Before
  public void setUp() throws Exception {
    XtsTradefedTestModuleResultsMonitor monitor = XtsTradefedTestModuleResultsMonitor.getInstance();
    Field runningInvocationsField =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredField("runningInvocations");
    runningInvocationsField.setAccessible(true);
    ((Map<?, ?>) runningInvocationsField.get(monitor)).clear();

    Field previousModuleResultsField =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredField("previousModuleResults");
    previousModuleResultsField.setAccessible(true);
    previousModuleResultsField.set(monitor, null);
  }

  // Mock class to mimic an invocation context that uses a standard Map.
  public static class MapInvocationContext {
    private final Map<String, List<String>> attributes = new HashMap<>();
    private final String invocationId;

    public MapInvocationContext(String invocationId) {
      this.invocationId = invocationId;
    }

    public String getInvocationId() {
      return invocationId;
    }

    public void addAttribute(String key, String value) {
      attributes.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public Map<String, List<String>> getAttributes() {
      return attributes;
    }
  }

  @Test
  public void getAttribute_withTradefedMultiMapContext_getsAttributes() throws Exception {
    MockInvocationContext context = new MockInvocationContext("inv-1");
    context.addAttribute("module-id", "test-module-id");

    Method getAttributeMethod =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredMethod(
            "getAttribute", Object.class, String.class);
    getAttributeMethod.setAccessible(true);

    String moduleId =
        (String)
            getAttributeMethod.invoke(
                XtsTradefedTestModuleResultsMonitor.getInstance(), context, "module-id");

    assertThat(moduleId).isEqualTo("test-module-id");
  }

  @Test
  public void getAttribute_withMapContext_getsAttributes() throws Exception {
    MapInvocationContext context = new MapInvocationContext("inv-2");
    context.addAttribute("module-id", "map-module-id");

    Method getAttributeMethod =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredMethod(
            "getAttribute", Object.class, String.class);
    getAttributeMethod.setAccessible(true);

    String moduleId =
        (String)
            getAttributeMethod.invoke(
                XtsTradefedTestModuleResultsMonitor.getInstance(), context, "module-id");

    assertThat(moduleId).isEqualTo("map-module-id");
  }

  @Test
  public void getAttribute_noAttributes_returnsEmpty() throws Exception {
    MockInvocationContext context = new MockInvocationContext("inv-3");

    Method getAttributeMethod =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredMethod(
            "getAttribute", Object.class, String.class);
    getAttributeMethod.setAccessible(true);

    String moduleId =
        (String)
            getAttributeMethod.invoke(
                XtsTradefedTestModuleResultsMonitor.getInstance(), context, "module-id");

    assertThat(moduleId).isEmpty();
  }

  @Test
  public void getInvocationId_returnsInvocationId() {
    MockInvocationContext context = new MockInvocationContext("inv-123");
    assertThat(XtsTradefedTestModuleResultsMonitor.getInvocationId(context)).isEqualTo("inv-123");
  }

  @Test
  public void getInvocationId_nullContext_returnsEmptyString() {
    assertThat(XtsTradefedTestModuleResultsMonitor.getInvocationId(null)).isEmpty();
  }

  @Test
  public void getInvocationId_nullId_returnsHashCode() {
    MockInvocationContext context = new MockInvocationContext(null);
    assertThat(XtsTradefedTestModuleResultsMonitor.getInvocationId(context))
        .isEqualTo(String.valueOf(System.identityHashCode(context)));
  }

  @Test
  public void monitor_tracksModuleProgress() throws Exception {
    XtsTradefedTestModuleResultsMonitor monitor = XtsTradefedTestModuleResultsMonitor.getInstance();

    MockInvocationContext context = new MockInvocationContext("inv-1");
    context.addAttribute("module-id", "test-module-id");

    monitor.onModuleStart(context, "inv-1");
    monitor.onTestRunStarted("inv-1", "run-1", 2);
    monitor.onTestEvent("inv-1", "testStarted", "test-1");
    monitor.onTestEvent("inv-1", "testEnded", "test-1");
    monitor.onTestEvent("inv-1", "testStarted", "test-2");
    monitor.onTestEvent("inv-1", "testFailed", "test-2");

    // Force an update
    java.lang.reflect.Method doUpdateMethod =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredMethod("doUpdate", Path.class);
    doUpdateMethod.setAccessible(true);

    Path resultsFile = tempFolder.newFile("results.txt").toPath();
    doUpdateMethod.invoke(monitor, resultsFile);

    String content = Files.readString(resultsFile);
    XtsTradefedTestModuleResults results = XtsTradefedTestModuleResults.decodeFromString(content);

    assertThat(results.runningModules()).containsKey("test-module-id");
    ModuleInfo module = results.runningModules().get("test-module-id");
    assertThat(module.id()).isEqualTo("test-module-id");
    assertThat(module.isRunning()).isTrue();
    assertThat(module.testsExpected()).isEqualTo(2);
    assertThat(module.testsCompleted()).isEqualTo(1);
    assertThat(module.testsFailed()).isEqualTo(1);
    assertThat(module.testsPassed()).isEqualTo(0);

    monitor.onModuleEnd("inv-1");
    doUpdateMethod.invoke(monitor, resultsFile);

    content = Files.readString(resultsFile);
    results = XtsTradefedTestModuleResults.decodeFromString(content);
    module = results.runningModules().get("test-module-id");
    assertThat(module.isRunning()).isFalse();
  }

  @Test
  public void monitor_mergesResultsFromMultipleInvocations() throws Exception {
    XtsTradefedTestModuleResultsMonitor monitor = XtsTradefedTestModuleResultsMonitor.getInstance();

    // Invocation 1: 1 pass, 1 expected
    MockInvocationContext context1 = new MockInvocationContext("inv-1");
    context1.addAttribute("module-id", "test-module-id");
    monitor.onModuleStart(context1, "inv-1");
    monitor.onTestRunStarted("inv-1", "run-1", 1);
    monitor.onTestEvent("inv-1", "testStarted", "test-1");
    monitor.onTestEvent("inv-1", "testEnded", "test-1");
    monitor.onModuleEnd("inv-1");

    // Invocation 2: 1 fail, 2 expected (total)
    MockInvocationContext context2 = new MockInvocationContext("inv-2");
    context2.addAttribute("module-id", "test-module-id");
    monitor.onModuleStart(context2, "inv-2");
    monitor.onTestRunStarted("inv-2", "run-1", 1);
    monitor.onTestEvent("inv-2", "testStarted", "test-2");
    monitor.onTestEvent("inv-2", "testFailed", "test-2");
    // test-2 not ended yet in this invocation
    monitor.onModuleEnd("inv-2");

    // Force an update
    Method doUpdateMethod =
        XtsTradefedTestModuleResultsMonitor.class.getDeclaredMethod("doUpdate", Path.class);
    doUpdateMethod.setAccessible(true);

    Path resultsFile = tempFolder.newFile("results_merged.txt").toPath();
    doUpdateMethod.invoke(monitor, resultsFile);

    String content = Files.readString(resultsFile);
    XtsTradefedTestModuleResults results = XtsTradefedTestModuleResults.decodeFromString(content);

    // Should only contain 1 module-id entry (the merged one)
    assertThat(results.runningModules()).hasSize(1);
    assertThat(results.runningModules()).containsKey("test-module-id");

    ModuleInfo mergedModule = results.runningModules().get("test-module-id");
    assertThat(mergedModule.id()).isEqualTo("test-module-id");
    assertThat(mergedModule.isRunning()).isFalse();
    assertThat(mergedModule.testsExpected()).isEqualTo(2);
    assertThat(mergedModule.testsCompleted()).isEqualTo(1); // Only test-1 ended
    assertThat(mergedModule.testsFailed()).isEqualTo(1); // test-2 failed
    assertThat(mergedModule.testsPassed()).isEqualTo(1); // test-1 passed
  }
}
