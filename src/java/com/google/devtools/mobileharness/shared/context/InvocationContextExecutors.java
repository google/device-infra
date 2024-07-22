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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Utility for decorating executors to propagate {@link InvocationContext} to sub threads. */
public class InvocationContextExecutors {

  /**
   * Decorates an executor to automatically propagate {@link InvocationContext} to sub threads.
   *
   * <p>Supported executor types include:
   *
   * <ul>
   *   <li>{@link Executor}
   *   <li>{@link ExecutorService}
   *   <li>{@link ScheduledExecutorService}
   *   <li>{@link ListeningExecutorService}
   *   <li>{@link ListeningScheduledExecutorService}
   * </ul>
   *
   * <p>Note that context will <b>also</b> be automatically propagate to {@linkplain
   * com.google.common.util.concurrent.ListenableFuture#addListener Listener} and {@linkplain
   * com.google.common.util.concurrent.FutureCallback FutureCallback} of returned {@linkplain
   * com.google.common.util.concurrent.ListenableFuture ListenableFuture}s.
   */
  public static <T extends Executor> T propagatingContext(T executor, Class<T> interfaceType) {
    return proxying(
        executor,
        interfaceType,
        InvocationContextPropagator::new,
        InvocationContextPropagator.class);
  }

  private static <T, I extends T, H extends InvocationHandler> T proxying(
      T object, Class<I> interfaceType, Function<T, H> handlerCreator, Class<H> handlerType) {
    if (Proxy.isProxyClass(object.getClass())
        && handlerType.isAssignableFrom(Proxy.getInvocationHandler(object).getClass())) {
      return object;
    }

    return interfaceType.cast(
        Proxy.newProxyInstance(
            InvocationContextExecutors.class.getClassLoader(),
            new Class<?>[] {interfaceType},
            handlerCreator.apply(object)));
  }

  private static class InvocationContextPropagator implements InvocationHandler {

    private final Executor executor;

    private InvocationContextPropagator(Executor executor) {
      this.executor = executor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      SubmitMethod submitMethod = SUBMIT_METHODS.get(method);
      if (submitMethod != null) {
        propagateContext(args, submitMethod);
      }
      Object returnValue;
      try {
        returnValue = method.invoke(executor, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      return decorateListenableFuture(returnValue);
    }

    /**
     * Replaces {@link Runnable}/{@link Callable}/{@link Collection<Callable>} in the arguments of
     * an executor method by {@link InvocationContext#propagateContext}.
     */
    @SuppressWarnings("unchecked")
    private static void propagateContext(Object[] args, SubmitMethod submitMethod) {
      int index = submitMethod.runnableArgumentIndex();
      if (submitMethod.runnableArgumentType().equals(Runnable.class)) {
        args[index] = InvocationContext.propagateContext((Runnable) args[index]);
      } else if (submitMethod.runnableArgumentType().equals(Callable.class)) {
        args[index] = InvocationContext.propagateContext((Callable<?>) args[index]);
      } else if (submitMethod.runnableArgumentType().equals(Collection.class)) {
        args[index] =
            ((Collection<Callable<?>>) args[index])
                .stream().map(InvocationContext::propagateContext).collect(toImmutableList());
      }
    }

    private static Object decorateListenableFuture(Object object) {
      if (object instanceof ListenableFuture<?>) {
        ListenableFuture<?> future = (ListenableFuture<?>) object;
        var interfaceType =
            future instanceof ListenableScheduledFuture<?>
                ? ListenableScheduledFuture.class
                : ListenableFuture.class;
        return proxying(
            future,
            interfaceType,
            ListenableFutureWithContext::new,
            ListenableFutureWithContext.class);
      } else {
        return object;
      }
    }
  }

  private static class ListenableFutureWithContext implements InvocationHandler {

    private static final Method ADD_LISTENER_METHOD;

    static {
      try {
        ADD_LISTENER_METHOD =
            ListenableFuture.class.getDeclaredMethod("addListener", Runnable.class, Executor.class);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("Failed to load methods of ListenableFuture", e);
      }
    }

    private final ListenableFuture<?> future;

    private ListenableFutureWithContext(ListenableFuture<?> future) {
      this.future = future;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.equals(ADD_LISTENER_METHOD)) {
        args[0] = InvocationContext.propagateContext((Runnable) args[0]);
      }
      try {
        return method.invoke(future, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }

  /**
   * A method of {@link Executor}/{@link ExecutorService}/{@link ScheduledExecutorService}/{@link
   * ListeningExecutorService}/{@link ListeningScheduledExecutorService} which receives a {@link
   * Runnable}/{@link Callable}/{@link Collection<Callable>} as its argument.
   */
  @AutoValue
  abstract static class SubmitMethod {

    abstract Method method();

    abstract int runnableArgumentIndex();

    /** If the type is {@link Collection}, it must be {@code Collection<Callable>}. */
    abstract Class<?> runnableArgumentType();

    @SuppressWarnings("SameParameterValue")
    private static SubmitMethod of(
        Method method, int runnableArgumentIndex, Class<?> runnableArgumentType) {
      return new AutoValue_InvocationContextExecutors_SubmitMethod(
          method, runnableArgumentIndex, runnableArgumentType);
    }
  }

  private static final ImmutableMap<Method, SubmitMethod> SUBMIT_METHODS;

  static {
    try {
      SUBMIT_METHODS =
          ImmutableList.of(
                  SubmitMethod.of(
                      Executor.class.getDeclaredMethod("execute", Runnable.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod("submit", Runnable.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod(
                          "submit", Runnable.class, Object.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod("submit", Callable.class),
                      0,
                      Callable.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod("invokeAll", Collection.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod(
                          "invokeAll", Collection.class, long.class, TimeUnit.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod("invokeAny", Collection.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ExecutorService.class.getDeclaredMethod(
                          "invokeAny", Collection.class, long.class, TimeUnit.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod("submit", Runnable.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod(
                          "submit", Runnable.class, Object.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod("submit", Callable.class),
                      0,
                      Callable.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod(
                          "invokeAll", Collection.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod(
                          "invokeAll", Collection.class, long.class, TimeUnit.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod(
                          "invokeAll", Collection.class, Duration.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ListeningExecutorService.class.getDeclaredMethod(
                          "invokeAny", Collection.class, Duration.class),
                      0,
                      Collection.class),
                  SubmitMethod.of(
                      ScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Runnable.class, long.class, TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Callable.class, long.class, TimeUnit.class),
                      0,
                      Callable.class),
                  SubmitMethod.of(
                      ScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleAtFixedRate",
                          Runnable.class,
                          long.class,
                          long.class,
                          TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleWithFixedDelay",
                          Runnable.class,
                          long.class,
                          long.class,
                          TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Runnable.class, long.class, TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Runnable.class, Duration.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Callable.class, long.class, TimeUnit.class),
                      0,
                      Callable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "schedule", Callable.class, Duration.class),
                      0,
                      Callable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleAtFixedRate",
                          Runnable.class,
                          long.class,
                          long.class,
                          TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleAtFixedRate", Runnable.class, Duration.class, Duration.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleWithFixedDelay",
                          Runnable.class,
                          long.class,
                          long.class,
                          TimeUnit.class),
                      0,
                      Runnable.class),
                  SubmitMethod.of(
                      ListeningScheduledExecutorService.class.getDeclaredMethod(
                          "scheduleWithFixedDelay", Runnable.class, Duration.class, Duration.class),
                      0,
                      Runnable.class))
              .stream()
              .collect(toImmutableMap(SubmitMethod::method, identity()));
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to load methods of executors", e);
    }
  }

  private InvocationContextExecutors() {}
}
