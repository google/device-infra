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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Monitor in TF process to monitor runtime info. */
public class XtsTradefedRuntimeInfoMonitor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final XtsTradefedRuntimeInfoMonitor INSTANCE = new XtsTradefedRuntimeInfoMonitor();

  private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(5L);
  private static final String MISSING_INVOCATION_ID = "missing-invocation-id";

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
      runningInvocations.putIfAbsent(
          Invocation.getInvocationId(invocationContext),
          new Invocation(
              /* isRunning= */ true,
              testInvocation,
              invocationContext,
              /* invocationEventHandler= */ null,
              /* exception= */ null));
    }
    triggerAsyncUpdate();
  }

  /**
   * Called when the {@link com.android.tradefed.invoker.TestInvocation#invoke} method exits. If
   * there's no exception, the invocation is removed from the running invocations map. Otherwise,
   * the invocation in the map is updated with the exception.
   *
   * <p>This is for when {@code TestInvocation} encounters an uncaught exception.
   */
  public void onInvocationExit(
      Object testInvocation, Object invocationContext, Throwable exception) {
    synchronized (runningInvocations) {
      if (exception != null) {
        runningInvocations.put(
            Invocation.getInvocationId(invocationContext),
            new Invocation(
                /* isRunning= */ false,
                testInvocation,
                invocationContext,
                /* invocationEventHandler= */ null,
                exception));
      } else {
        runningInvocations.remove(Invocation.getInvocationId(invocationContext));
      }
    }
    triggerAsyncUpdate();
  }

  public void onInvocationComplete(
      Object invocationEventHandler, Object invocationContext, Throwable exception) {
    synchronized (runningInvocations) {
      Invocation invocation =
          new Invocation(
              /* isRunning= */ false,
              /* testInvocation= */ null,
              invocationContext,
              invocationEventHandler,
              exception);
      // Only add to the running invocations map if there is an error message (to be saved to the
      // file).
      if (!invocation.getErrorMessage().isEmpty()) {
        runningInvocations.putIfAbsent(Invocation.getInvocationId(invocationContext), invocation);
      }
    }
    triggerAsyncUpdate();
  }

  /**
   * Called when the {@link com.android.tradefed.result.CollectingTestListener#invocationFailed}
   * method is entered.
   *
   * <p>This is for when {@code TestInvocation} explicitly catches an exception, and forwards it to
   * the {@code CollectingTestListener} to handle.
   */
  public void onInvocationFailed(Throwable exception) {
    synchronized (runningInvocations) {
      runningInvocations.putIfAbsent(
          // In this case, the invocation context is not available, so we use a fake ID, instead of
          // invocation ID, as the key for the map:
          MISSING_INVOCATION_ID,
          new Invocation(
              /* isRunning= */ false,
              /* testInvocation= */ null,
              /* invocationContext= */ null,
              /* invocationEventHandler= */ null,
              exception));
    }
    triggerAsyncUpdate();
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

    private final boolean isRunning;

    /** {@code com.android.tradefed.invoker.ITestInvocation}. */
    @Nullable private final Object testInvocation;

    /** {@code com.android.tradefed.invoker.IInvocationContext}. */
    private final Object invocationContext;

    /** {@code com.android.tradefed.cluster.ClusterCommandScheduler.InvocationEventHandler}. */
    @Nullable private final Object invocationEventHandler;

    @Nullable private final Throwable exception;

    /** TradefedInvocation generated from last {@link #update()}. */
    private volatile TradefedInvocation previousInvocation;

    private Invocation(
        boolean isRunning,
        Object testInvocation,
        Object invocationContext,
        Object invocationEventHandler,
        Throwable exception) {
      this.isRunning = isRunning;
      this.testInvocation = testInvocation;
      this.invocationContext = invocationContext;
      this.invocationEventHandler = invocationEventHandler;
      this.exception = exception;
      this.previousInvocation = TradefedInvocation.getDefaultInstance();
    }

    /**
     * Whether {@link #update()} will generate a different TradefedInvocation compared with {@link
     * #previousInvocation}.
     */
    private boolean needUpdate() {
      TradefedInvocation previousInvocation = this.previousInvocation;
      return previousInvocation.isRunning() != isRunning
          || !previousInvocation.status().equals(getInvocationStatus())
          || !previousInvocation.deviceIds().equals(getDeviceIds())
          || !previousInvocation.errorMessage().equals(getErrorMessage());
    }

    /** Updates and returns a new {@link #previousInvocation}. */
    private TradefedInvocation update() {
      TradefedInvocation newInvocation =
          new TradefedInvocation(
              isRunning, getDeviceIds(), getInvocationStatus(), getErrorMessage());
      previousInvocation = newInvocation;
      return newInvocation;
    }

    private String getInvocationStatus() {
      return testInvocation != null ? testInvocation.toString() : "";
    }

    /**
     * The error message comes from either the {@code mError} field of {@code
     * InvocationEventHandler} or the Throwable exception thrown by the monitored methods.
     */
    private String getErrorMessage() {
      if (exception != null) {
        return printThrowable(exception);
      } else if (invocationEventHandler != null) {
        try {
          Field errorMessageField = invocationEventHandler.getClass().getDeclaredField("mError");
          errorMessageField.setAccessible(true);
          String value = (String) errorMessageField.get(invocationEventHandler);
          return value != null && !value.isEmpty() ? value : "";
        } catch (ReflectiveOperationException e) {
          throw new LinkageError("Failed to read the mError field of InvocationEventHandler", e);
        }
      } else {
        return "";
      }
    }

    @SuppressWarnings("unchecked")
    private List<String> getDeviceIds() {
      try {
        return invocationContext == null
            ? Collections.<String>emptyList()
            : (List<String>)
                invocationContext.getClass().getMethod("getSerials").invoke(invocationContext);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("Failed to call IInvocationContext.getSerials()", e);
      }
    }

    private static String getInvocationId(Object invocationContext) {
      try {
        return invocationContext == null
            ? MISSING_INVOCATION_ID
            : (String)
                invocationContext.getClass().getMethod("getInvocationId").invoke(invocationContext);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("Failed to call IInvocationContext.getInvocationId()", e);
      }
    }

    private static String printThrowable(Throwable e) {
      StringWriter stringWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stringWriter));
      return stringWriter.toString();
    }
  }
}
