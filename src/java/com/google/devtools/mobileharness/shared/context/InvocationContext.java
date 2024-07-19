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

    private final String name;

    InvocationType(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** An auto closeable scope of context. */
  public static class ContextScope implements NonThrowingAutoCloseable {

    private final Map<InvocationType, String> scopedContext;

    /**
     * Adds the given {@code scopedContext} to the current context, and removes them when this
     * object is {@linkplain NonThrowingAutoCloseable#close() closed}.
     */
    public ContextScope(Map<InvocationType, String> scopedContext) {
      this.scopedContext = new EnumMap<>(scopedContext);
      getCurrentContext().putAll(this.scopedContext);
    }

    @Override
    public void close() {
      Map<InvocationType, String> currentContext = getCurrentContext();
      scopedContext.forEach((type, value) -> currentContext.remove(type));
    }
  }

  private static final ThreadLocal<InvocationContext> CONTEXT =
      ThreadLocal.withInitial(InvocationContext::new);

  /** Callers should not modify the returned map. */
  public static Map<InvocationType, String> getCurrentContext() {
    return CONTEXT.get().context;
  }

  /** Returns an immutable snapshot of the current context. */
  public static ImmutableMap<InvocationType, String> getCurrentContextImmutable() {
    return ImmutableMap.copyOf(getCurrentContext());
  }

  /** Propagated the current context to the thread that runs the given {@code callable}. */
  public static <V> Callable<V> propagateContext(Callable<V> callable) {
    checkNotNull(callable);
    Map<InvocationType, String> context = new EnumMap<>(getCurrentContext());
    return new CallableWithContext<>(callable, context);
  }

  /** Propagated the current context to the thread that runs the given {@code futureCallback}. */
  public static <V> FutureCallback<V> propagateContext(FutureCallback<V> futureCallback) {
    checkNotNull(futureCallback);
    Map<InvocationType, String> context = new EnumMap<>(getCurrentContext());
    return new FutureCallbackWithContext<>(futureCallback, context);
  }

  private final Map<InvocationType, String> context = new EnumMap<>(InvocationType.class);

  private InvocationContext() {}

  private static class CallableWithContext<V> implements Callable<V> {

    private final Callable<V> callable;
    private final Map<InvocationType, String> context;

    private CallableWithContext(Callable<V> callable, Map<InvocationType, String> context) {
      this.callable = callable;
      this.context = context;
    }

    @Override
    public V call() throws Exception {
      try (ContextScope ignored = new ContextScope(context)) {
        return callable.call();
      }
    }
  }

  private static class FutureCallbackWithContext<V> implements FutureCallback<V> {

    private final FutureCallback<V> futureCallback;
    private final Map<InvocationType, String> context;

    private FutureCallbackWithContext(
        FutureCallback<V> futureCallback, Map<InvocationType, String> context) {
      this.futureCallback = futureCallback;
      this.context = context;
    }

    @Override
    public void onSuccess(V result) {
      try (ContextScope ignored = new ContextScope(context)) {
        futureCallback.onSuccess(result);
      }
    }

    @Override
    public void onFailure(Throwable t) {
      try (ContextScope ignored = new ContextScope(context)) {
        futureCallback.onFailure(t);
      }
    }
  }
}
