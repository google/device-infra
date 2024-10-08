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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.GuardedBy;

/** Monitor in TF process to monitor runtime info. */
public class XtsTradefedRuntimeInfoMonitor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final XtsTradefedRuntimeInfoMonitor INSTANCE = new XtsTradefedRuntimeInfoMonitor();

  private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(5L);

  public static XtsTradefedRuntimeInfoMonitor getInstance() {
    return INSTANCE;
  }

  private final XtsTradefedRuntimeInfoFileUtil runtimeInfoFileUtil =
      new XtsTradefedRuntimeInfoFileUtil();

  @GuardedBy("itself")
  private final Map<Object, Invocation> runningInvocations = new LinkedHashMap<>();

  private final Object needUpdateLock = new Object();

  @GuardedBy("needUpdateLock")
  private boolean needUpdate;

  private XtsTradefedRuntimeInfoMonitor() {}

  public void start(Path runtimeInfoFilePath) {
    Thread thread = new Thread(() -> run(runtimeInfoFilePath), "runtime-info-monitor");
    thread.setDaemon(true);
    thread.start();
  }

  public void onInvocationEnter(Object testInvocation, Object invocationContext) {
    synchronized (runningInvocations) {
      runningInvocations.computeIfAbsent(
          testInvocation, invocation -> new Invocation(invocation, invocationContext));
    }
    triggerAsyncUpdate();
  }

  public void onInvocationExit(Object testInvocation) {
    synchronized (runningInvocations) {
      runningInvocations.remove(testInvocation);
    }
    triggerAsyncUpdate();
  }

  public void onInvocationComplete(Object invocationEventHandler, Object invocationContext) {
    // TODO: Implements the method.
  }

  private void run(Path runtimeInfoFilePath) {
    try {
      while (!Thread.interrupted()) {
        // Sleeps and waits for invocation starts/ends.
        boolean needUpdate;
        synchronized (needUpdateLock) {
          if (!this.needUpdate) {
            needUpdateLock.wait(UPDATE_INTERVAL.toMillis());
          }
          needUpdate = this.needUpdate;
          this.needUpdate = false;
        }

        // Checks invocation status changes.
        if (!needUpdate) {
          synchronized (runningInvocations) {
            for (Invocation invocation : runningInvocations.values()) {
              if (invocation.needUpdate()) {
                needUpdate = true;
                break;
              }
            }
          }
        }

        // Updates invocations to file.
        if (needUpdate) {
          List<TradefedInvocation> invocations = new ArrayList<>();
          synchronized (runningInvocations) {
            for (Invocation invocation : runningInvocations.values()) {
              invocations.add(invocation.update());
            }
          }

          XtsTradefedRuntimeInfo runtimeInfo =
              new XtsTradefedRuntimeInfo(invocations, Instant.now());
          doUpdate(runtimeInfoFilePath, runtimeInfo);
        }
      }
    } catch (InterruptedException e) {
      logger.atInfo().log("Interrupted");
    } catch (RuntimeException | Error e) {
      logger.atWarning().withCause(e).log("Error in runtime info monitor");
    }
  }

  private void doUpdate(Path runtimeInfoFilePath, XtsTradefedRuntimeInfo runtimeInfo) {
    try {
      runtimeInfoFileUtil.writeInfo(runtimeInfoFilePath, runtimeInfo);
    } catch (IOException | RuntimeException | Error e) {
      logger.atWarning().withCause(e).log(
          "Failed to write runtime info to %s", runtimeInfoFilePath);
    }
  }

  private void triggerAsyncUpdate() {
    synchronized (needUpdateLock) {
      needUpdate = true;
      needUpdateLock.notifyAll();
    }
  }

  private static class Invocation {

    /** {@code com.android.tradefed.invoker.ITestInvocation}. */
    private final Object testInvocation;

    /** {@code com.android.tradefed.invoker.IInvocationContext}. */
    private final Object invocationContext;

    /** TradefedInvocation generated from last {@link #update()}. */
    private volatile TradefedInvocation previousInvocation;

    private Invocation(Object testInvocation, Object invocationContext) {
      this.testInvocation = testInvocation;
      this.invocationContext = invocationContext;
      this.previousInvocation = TradefedInvocation.getDefaultInstance();
    }

    /**
     * Whether {@link #update()} will generate a different TradefedInvocation compared with {@link
     * #previousInvocation}.
     */
    private boolean needUpdate() {
      TradefedInvocation previousInvocation = this.previousInvocation;
      return !previousInvocation.status().equals(getInvocationStatus())
          || !previousInvocation.deviceIds().equals(getDeviceIds());
    }

    /** Updates and returns a new {@link #previousInvocation}. */
    private TradefedInvocation update() {
      TradefedInvocation newInvocation =
          new TradefedInvocation(getDeviceIds(), getInvocationStatus());
      previousInvocation = newInvocation;
      return newInvocation;
    }

    private String getInvocationStatus() {
      return testInvocation.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getDeviceIds() {
      try {
        return (List<String>)
            invocationContext.getClass().getMethod("getSerials").invoke(invocationContext);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("Failed to call IInvocationContext.getSerials()", e);
      }
    }
  }
}
