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
import static java.util.stream.Collectors.toMap;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults.ModuleInfo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A monitor that runs in a Tradefed invocation agent, collects test module execution progress, and
 * saves the progress to a file periodically.
 */
public class XtsTradefedTestModuleResultsMonitor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The interval to update the module results file with the latest progress.
  private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(10L);

  private static final XtsTradefedTestModuleResultsMonitor INSTANCE =
      new XtsTradefedTestModuleResultsMonitor();

  public static XtsTradefedTestModuleResultsMonitor getInstance() {
    return INSTANCE;
  }

  // runningInvocations mapping from a unique invocation ID to Invocation instance.
  // Because Tradefed invocations may run in parallel within the same JVM, we key them by a unique
  // invocation ID derived from the module context object.
  private final Map<String, Invocation> runningInvocations = new ConcurrentHashMap<>();

  private final Object fileLock = new Object();
  // Used to decide if we need to update the module results file.
  private XtsTradefedTestModuleResults previousModuleResults = null;

  private XtsTradefedTestModuleResultsMonitor() {}

  /** Starts the monitor. */
  public void start(Path moduleResultsFilePath) {
    Thread thread =
        new Thread(() -> run(moduleResultsFilePath), "xts-tradefed-test-module-results-monitor");
    thread.setDaemon(true);
    thread.start();
  }

  private void run(Path moduleResultsFilePath) {
    try {
      while (!Thread.interrupted()) {
        Thread.sleep(UPDATE_INTERVAL.toMillis());
        try {
          doUpdate(moduleResultsFilePath);
        } catch (RuntimeException | Error e) {
          logger.atWarning().withCause(e).log(
              "Fatal error in XTS Tradefed test module results monitor");
        }
      }
    } catch (InterruptedException e) {
      logger.atInfo().log("XTS Tradefed test module results monitor interrupted");
      Thread.currentThread().interrupt();
    }
  }

  /** Gets the invocation ID from the module context object. */
  public static String getInvocationId(Object moduleContext) {
    if (moduleContext == null) {
      return "";
    }
    try {
      Object invocationId =
          moduleContext.getClass().getMethod("getInvocationId").invoke(moduleContext);
      if (invocationId != null) {
        return invocationId.toString();
      }
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to get invocation ID from module context", e);
    }
    return String.valueOf(System.identityHashCode(moduleContext));
  }

  /** Gets an attribute (e.g. module name) from the module context object. */
  private String getAttribute(Object moduleContext, String attributeName) {
    try {
      Object attributes = moduleContext.getClass().getMethod("getAttributes").invoke(moduleContext);
      if (attributes != null) {
        Object attribute =
            attributes.getClass().getMethod("get", Object.class).invoke(attributes, attributeName);
        if (attribute instanceof List && !((List<?>) attribute).isEmpty()) {
          return ((List<?>) attribute).get(0).toString();
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to get attribute from module context", e);
    }
    return "";
  }

  /** Called when a test module starts execution. */
  public void onModuleStart(Object moduleContext, String invocationId) {
    // The "module-id" attribute is something like "arm64-v8a CtsUsbTests[instant]" while the
    // "module-name" is "CtsUsbTests".
    String moduleId = getAttribute(moduleContext, "module-id");

    Invocation invocation =
        runningInvocations.computeIfAbsent(invocationId, unused -> new Invocation());
    invocation.onModuleStart(moduleId);
  }

  /** Called when a test module ends execution. */
  public void onModuleEnd(String invocationId) {
    Invocation invocation = runningInvocations.get(invocationId);
    if (invocation != null) {
      invocation.onModuleEnd();
    }
  }

  /**
   * Called when a test run starts execution.
   *
   * @param invocationId the invocation ID
   * @param runName the name of the test run, e.g. "arm64-v8a CtsUsbTests[instant]"
   * @param testCount the number of tests in the test run.
   */
  public void onTestRunStarted(String invocationId, String runName, int testCount) {
    Invocation invocation = runningInvocations.get(invocationId);
    if (invocation != null) {
      invocation.onTestRunStarted(runName, testCount);
    }
  }

  /** Called when a test run ends execution. */
  public void onTestRunEnded(String invocationId, Duration elapsedTime) {
    Invocation invocation = runningInvocations.get(invocationId);
    if (invocation != null) {
      invocation.onTestRunEnded(elapsedTime);
    }
  }

  /**
   * Called when a test event occurs.
   *
   * @param invocationId the invocation ID
   * @param eventType the test event type, e.g. "testStarted", "testEnded", etc.
   * @param testId the test ID, e.g.
   *     "com.android.cts.usb.TestUsbTest#testInstantAppsCannotReadSerial"
   */
  public void onTestEvent(String invocationId, String eventType, String testId) {
    Invocation invocation = runningInvocations.get(invocationId);
    if (invocation != null) {
      invocation.onTestEvent(eventType, testId);
    }
  }

  /** Updates the module results file with the latest progress. */
  private void doUpdate(Path moduleResultsFilePath) {
    if (runningInvocations.isEmpty()) {
      return;
    }

    // Merge module info from all invocations/shards.
    Map<String, ModuleInfo> mergedModules =
        runningInvocations.values().stream()
            .flatMap(invocation -> invocation.getModules().stream())
            .collect(
                toMap(
                    /* keyMapper= */ ModuleInfo::id,
                    /* valueMapper= */ Function.identity(),
                    /* mergeFunction= */ XtsTradefedTestModuleResultsMonitor::mergeModuleInfo));

    XtsTradefedTestModuleResults moduleResults = new XtsTradefedTestModuleResults(mergedModules);

    if (moduleResults.equals(previousModuleResults)) {
      return;
    }
    previousModuleResults = moduleResults;

    synchronized (fileLock) {
      try {
        try (FileChannel lockFile =
                FileChannel.open(
                    moduleResultsFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock ignored = lockFile.lock()) {
          lockFile.truncate(0);
          lockFile.write(ByteBuffer.wrap(moduleResults.encodeToString().getBytes(UTF_8)));
        }
        logger.atFine().log("Updated module results info: %s", moduleResults);
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to write module results to file %s", moduleResultsFilePath);
      }
    }
  }

  private static ModuleInfo mergeModuleInfo(ModuleInfo module1, ModuleInfo module2) {
    return new ModuleInfo(
        module1.id(),
        module1.isRunning() || module2.isRunning(),
        module1.testsExpected() + module2.testsExpected(),
        module1.testsCompleted() + module2.testsCompleted(),
        module1.testsFailed() + module2.testsFailed(),
        module1.testsPassed() + module2.testsPassed(),
        module1.testsSkipped() + module2.testsSkipped(),
        module1.duration().plus(module2.duration()));
  }

  private static class Invocation {

    private final ConcurrentHashMap<Long, ModuleProgress> threadModules = new ConcurrentHashMap<>();
    private final List<ModuleProgress> completedModules = new CopyOnWriteArrayList<>();

    private Invocation() {}

    private void onModuleStart(String moduleId) {
      ModuleProgress module = new ModuleProgress(moduleId, /* isRunning= */ true);
      threadModules.put(currentThreadId(), module);
    }

    private void onModuleEnd() {
      ModuleProgress lastModule = threadModules.remove(currentThreadId());
      if (lastModule != null && lastModule.isRunning.get()) {
        lastModule.isRunning.set(false);
        completedModules.add(lastModule);
      }
    }

    private void onTestRunStarted(String runName, int testCount) {
      ModuleProgress lastModule = threadModules.get(currentThreadId());
      if (lastModule != null && lastModule.isRunning.get()) {
        if (lastModule.startedRuns.add(runName)) {
          lastModule.testsExpected.addAndGet(testCount);
        }
      }
    }

    private void onTestRunEnded(Duration elapsedTime) {
      ModuleProgress lastModule = threadModules.get(currentThreadId());
      if (lastModule != null && lastModule.isRunning.get()) {
        lastModule.accumulatedRunDurationMs.addAndGet(elapsedTime.toMillis());
      }
    }

    private void onTestEvent(String eventType, String testId) {
      ModuleProgress lastModule = threadModules.get(currentThreadId());
      if (lastModule == null || !lastModule.isRunning.get()) {
        return;
      }
      switch (eventType) {
        case "testStarted" -> {
          lastModule.failedTests.remove(testId);
          lastModule.skippedTests.remove(testId);
        }
        case "testEnded" -> {
          lastModule.completedTests.add(testId);
        }
        case "testFailed", "testAssumptionFailure" -> {
          lastModule.failedTests.add(testId);
        }
        case "testIgnored", "testSkipped" -> {
          lastModule.skippedTests.add(testId);
        }
        default -> {}
      }
    }

    private List<ModuleInfo> getModules() {
      List<ModuleInfo> currentModules =
          new ArrayList<>(threadModules.size() + completedModules.size());
      for (ModuleProgress module : threadModules.values()) {
        currentModules.add(module.toModuleInfo());
      }
      for (ModuleProgress module : completedModules) {
        currentModules.add(module.toModuleInfo());
      }
      return currentModules;
    }

    @SuppressWarnings(
        "deprecation") // Not using the #threadId() method since it's only available in Java 19+.
    private static long currentThreadId() {
      return Thread.currentThread().getId();
    }
  }

  private static class ModuleProgress {
    private final String id;
    private final AtomicBoolean isRunning;
    private final AtomicInteger testsExpected = new AtomicInteger();
    private final Set<String> startedRuns = ConcurrentHashMap.newKeySet();
    private final Set<String> completedTests = ConcurrentHashMap.newKeySet();
    private final Set<String> failedTests = ConcurrentHashMap.newKeySet();
    private final Set<String> skippedTests = ConcurrentHashMap.newKeySet();
    private final AtomicLong accumulatedRunDurationMs = new AtomicLong(0);

    private ModuleProgress(String id, boolean isRunning) {
      this.id = id;
      this.isRunning = new AtomicBoolean(isRunning);
    }

    private ModuleInfo toModuleInfo() {
      int testsPassed =
          Math.max(0, completedTests.size() - failedTests.size() - skippedTests.size());
      return new ModuleInfo(
          id,
          isRunning.get(),
          testsExpected.get(),
          completedTests.size(),
          failedTests.size(),
          testsPassed,
          skippedTests.size(),
          Duration.ofMillis(accumulatedRunDurationMs.get()));
    }
  }
}
