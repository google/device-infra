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

package com.google.devtools.mobileharness.infra.controller.test;

import com.google.auto.value.AutoValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/**
 * A wrapper object containing thread-specific test information, such as {@link TestLocator}.
 *
 * <p>Any code that is running as part of a test should have access to the information about that
 * test. This class allows that information to be obtained statically, rather than having to be
 * passed from object-to-object.
 *
 * <p>Code that is executing on the main thread will have its test context populated by {@link
 * BaseTestRunner#execute}. For multi-threaded components of tests (such as testbeds), the threading
 * code needs to wrap the execution in either a {@link TestContextRunnable} or {@link
 * TestContextCallable}. This will propagate the test context to the execution of the underlying
 * Runnables/Callables, and clean up afterwards, allowing this mechanism to be used even with
 * long-lived or shared threadpools.
 *
 * <p>To inspect the test context, simply use {@link TestContext#get}. Note that the context is an
 * immutable value type that simply stores the defined context. This means that if a context object
 * is passed to or shared with code executing in a different test context, it will still contain the
 * context for the callsite of get().
 *
 * <p>To set up a new context, use one of the {@link TestContext#set} methods within a
 * try-with-resources block. This will ensure that the context does not leak outside of the desired
 * scope. For example:
 *
 * <pre>
 *   try (WithTraceContext context = TraceContext.set(testLocator)) {
 *     doWorkRequiringTestContext();
 *   }
 * </pre>
 *
 * <p>Note that in the current implementation, tests are assumed to be disjoint executions, not
 * nested. As a result, when we clean up a test context at the end of a try-with-resource block, the
 * context is cleared entirely. We do not save the previous context and re-apply it.
 */
@AutoValue
public abstract class TestContext {

  public static final TestContext EMPTY;

  private static final ThreadLocal<TestContext> LOCAL_TEST_CONTEXT;

  static {
    EMPTY = createEmpty();
    LOCAL_TEST_CONTEXT =
        new ThreadLocal<TestContext>() {
          @Override
          protected TestContext initialValue() {
            return EMPTY;
          }
        };
  }

  /**
   * Gets the {@link TestContext} for the current thread.
   *
   * @return the TestContext. Will not be null even if no context has been initialized.
   */
  public static synchronized TestContext get() {
    return LOCAL_TEST_CONTEXT.get();
  }

  /**
   * Initializes the current context with the given {@link TestContext}.
   *
   * @param testContext the context to initialize with
   * @return an {@link AutoCloseable} for cleaning up the context
   */
  public static synchronized WithTestContext set(TestContext testContext) {
    LOCAL_TEST_CONTEXT.set(testContext);
    return new WithTestContext();
  }

  /**
   * Initializes the current context with the given {@link TestLocator}.
   *
   * @param testLocator the locator to initialize the context with
   * @return an {@link AutoCloseable} for cleaning up the context
   */
  public static synchronized WithTestContext set(TestLocator testLocator) {
    return set(create(testLocator));
  }

  /** Clears the current context back to a blank state. */
  public static synchronized void clear() {
    LOCAL_TEST_CONTEXT.remove();
  }

  private static TestContext create(TestLocator testLocator) {
    JobLocator jobLocator = testLocator.getJobLocator();
    return new AutoValue_TestContext(testLocator, jobLocator);
  }

  private static TestContext createEmpty() {
    return new AutoValue_TestContext(null, null);
  }

  @Nullable
  public abstract TestLocator testLocator();

  @Nullable
  public abstract JobLocator jobLocator();

  /** An {@link AutoCloseable} that will clean up the current context on close. */
  public static class WithTestContext implements AutoCloseable {
    @Override
    public void close() {
      TestContext.clear();
    }
  }

  /**
   * A {@link Runnable} that will propagate {@link TestContext} from one thread to another.
   *
   * <p>The context will be applied for the execution of the {@link Runnable}'s run() method, and
   * cleaned up afterwards.
   *
   * <p>The context used is the current thread context at the time of this object's creation.
   */
  public static class TestContextRunnable implements Runnable {

    private final TestContext creatorContext;
    private final Runnable runnable;

    /**
     * Constructor that accepts a {@link Runnable}. The run() method of the provided Runnable will
     * be executed within the same test context as that of this constructor.
     */
    public TestContextRunnable(Runnable runnable) {
      creatorContext = TestContext.get();
      this.runnable = runnable;
    }

    @Override
    public final void run() {
      try (WithTestContext context = TestContext.set(creatorContext)) {
        runnable.run();
      }
    }
  }

  /**
   * A {@link Callable} that will propagate {@link TestContext} from one thread to another.
   *
   * <p>The context will be applied for the execution of the {@link Callable}'s call() method, and
   * cleaned up afterwards.
   *
   * <p>The context used is the current thread context at the time of this object's creation.
   */
  public static class TestContextCallable<T> implements Callable<T> {
    private final TestContext creatorContext;
    private final Callable<T> callable;

    /**
     * Constructor that accepts a {@link Callable}. The call() method of the provided Runnable will
     * be executed within the same test context as that of this constructor.
     */
    public TestContextCallable(Callable<T> callable) {
      creatorContext = TestContext.get();
      this.callable = callable;
    }

    @Override
    public final T call() throws Exception {
      try (WithTestContext context = TestContext.set(creatorContext)) {
        return callable.call();
      }
    }
  }
}
