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

package com.google.devtools.mobileharness.shared.context;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.concurrent.NotThreadSafe;

/** Invocation context. */
@NotThreadSafe
public class InvocationContext {

  /** Invocation type. */
  public enum InvocationType {
    OMNILAB_TEST("test_id"),
    OMNILAB_JOB("job_id"),
    OLC_SESSION("olc_session_id"),
    OLC_CLIENT("olc_client_id");

    private final String displayName;

    InvocationType(String displayName) {
      this.displayName = displayName;
    }

    public String displayName() {
      return displayName;
    }

    @Override
    public String toString() {
      return displayName();
    }
  }

  /** Invocation information. */
  @AutoValue
  public abstract static class InvocationInfo {

    public abstract String id();

    public abstract String displayId();

    @Override
    public final String toString() {
      return displayId();
    }

    public static InvocationInfo of(String id, String displayId) {
      return new AutoValue_InvocationContext_InvocationInfo(id, displayId);
    }

    public static InvocationInfo fromUuid(String uuid) {
      return of(uuid, uuid.substring(0, min(8, uuid.length())));
    }

    public static InvocationInfo sameDisplayId(String id) {
      return of(id, id);
    }
  }

  /** An auto closeable scope of context. */
  public static class ContextScope implements NonThrowingAutoCloseable {

    private final Map<InvocationType, InvocationInfo> scopedContext;

    /**
     * Adds the given {@code scopedContext} to the current context, and removes them when this
     * object is {@linkplain NonThrowingAutoCloseable#close() closed}.
     */
    public ContextScope(Map<InvocationType, InvocationInfo> scopedContext) {
      this.scopedContext = copy(scopedContext);
      getCurrentContext().putAll(this.scopedContext);
    }

    @Override
    public void close() {
      Map<InvocationType, InvocationInfo> currentContext = getCurrentContext();
      scopedContext.forEach((type, value) -> currentContext.remove(type));
    }
  }

  private static final ThreadLocal<InvocationContext> CONTEXT =
      ThreadLocal.withInitial(InvocationContext::new);

  /** Callers should not modify the returned map. */
  public static Map<InvocationType, InvocationInfo> getCurrentContext() {
    return CONTEXT.get().context;
  }

  /** Returns an immutable snapshot of the current context. */
  public static ImmutableMap<InvocationType, InvocationInfo> getCurrentContextImmutable() {
    return ImmutableMap.copyOf(getCurrentContext());
  }

  /** Propagated the current context to the thread that runs the given {@code runnable}. */
  public static Runnable propagateContext(Runnable runnable) {
    checkNotNull(runnable);
    Map<InvocationType, InvocationInfo> parentContext = getCurrentContext();
    Map<InvocationType, InvocationInfo> context = copy(parentContext);
    return new RunnableWithContext(runnable, context, parentContext);
  }

  /** Propagated the current context to the thread that runs the given {@code callable}. */
  public static <V> Callable<V> propagateContext(Callable<V> callable) {
    checkNotNull(callable);
    Map<InvocationType, InvocationInfo> parentContext = getCurrentContext();
    Map<InvocationType, InvocationInfo> context = copy(parentContext);
    return new CallableWithContext<>(callable, context, parentContext);
  }

  /** Propagated the current context to the thread that runs the given {@code futureCallback}. */
  public static <V> FutureCallback<V> propagateContext(FutureCallback<V> futureCallback) {
    checkNotNull(futureCallback);
    Map<InvocationType, InvocationInfo> parentContext = getCurrentContext();
    Map<InvocationType, InvocationInfo> context = copy(parentContext);
    return new FutureCallbackWithContext<>(futureCallback, context, parentContext);
  }

  private final Map<InvocationType, InvocationInfo> context = new EnumMap<>(InvocationType.class);

  private InvocationContext() {}

  private static class RunnableWithContext implements Runnable {

    private final Runnable runnable;
    private final Map<InvocationType, InvocationInfo> context;
    private final Object parentContext;

    private RunnableWithContext(
        Runnable runnable, Map<InvocationType, InvocationInfo> context, Object parentContext) {
      this.runnable = runnable;
      this.context = context;
      this.parentContext = parentContext;
    }

    @Override
    public void run() {
      if (getCurrentContext() == parentContext) {
        runnable.run();
      } else {
        try (ContextScope ignored = new ContextScope(context)) {
          runnable.run();
        }
      }
    }
  }

  private static class CallableWithContext<V> implements Callable<V> {

    private final Callable<V> callable;
    private final Map<InvocationType, InvocationInfo> context;
    private final Object parentContext;

    private CallableWithContext(
        Callable<V> callable, Map<InvocationType, InvocationInfo> context, Object parentContext) {
      this.callable = callable;
      this.context = context;
      this.parentContext = parentContext;
    }

    @Override
    public V call() throws Exception {
      if (getCurrentContext() == parentContext) {
        return callable.call();
      } else {
        try (ContextScope ignored = new ContextScope(context)) {
          return callable.call();
        }
      }
    }
  }

  private static class FutureCallbackWithContext<V> implements FutureCallback<V> {

    private final FutureCallback<V> futureCallback;
    private final Map<InvocationType, InvocationInfo> context;
    private final Object parentContext;

    private FutureCallbackWithContext(
        FutureCallback<V> futureCallback,
        Map<InvocationType, InvocationInfo> context,
        Object parentContext) {
      this.futureCallback = futureCallback;
      this.context = context;
      this.parentContext = parentContext;
    }

    @Override
    public void onSuccess(V result) {
      if (getCurrentContext() == parentContext) {
        futureCallback.onSuccess(result);
      } else {
        try (ContextScope ignored = new ContextScope(context)) {
          futureCallback.onSuccess(result);
        }
      }
    }

    @Override
    public void onFailure(Throwable t) {
      if (getCurrentContext() == parentContext) {
        futureCallback.onFailure(t);
      } else {
        try (ContextScope ignored = new ContextScope(context)) {
          futureCallback.onFailure(t);
        }
      }
    }
  }

  private static Map<InvocationType, InvocationInfo> copy(Map<InvocationType, InvocationInfo> map) {
    return map.isEmpty() ? new EnumMap<>(InvocationType.class) : new EnumMap<>(map);
  }
}
