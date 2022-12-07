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

package com.google.devtools.mobileharness.shared.util.quota;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quota manager for managing quotas among processes.
 *
 * <p>For example, if you want to limit ADB large file pushing concurrency on a machine, you can use
 * the following code:
 *
 * <pre>
 * try (Lease ignored = QuotaManager.getInstance().acquire(QuotaKey.ADB_PUSH_LARGE_FILE, 1)) {
 *   ... // Does ADB push
 * }
 * </pre>
 *
 * <p>If a quota {@code key} is not in the managed quota list, {@link #acquire} and {@link
 * Lease#release} on it can be freely invoked and these invocations will just return and do nothing.
 */
public abstract class QuotaManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static QuotaManager getInstance() {
    return INSTANCE;
  }

  /** Lease of some acquired quotas. */
  public interface Lease extends AutoCloseable {

    /**
     * Releases the acquired quotas.
     *
     * <p>Repeated invocations of this method should not have side effect.
     */
    void release();

    /**
     * Equivalent to {@link #release()}.
     *
     * <p>{@inheritDoc}
     */
    @Override
    default void close() {
      release();
    }
  }

  /** Base implementation of lease which prevents repeated releasing and checks unreleased ones. */
  abstract static class BaseLease implements Lease {

    private final AtomicBoolean isReleased = new AtomicBoolean();

    @Override
    public final void release() {
      if (!isReleased.getAndSet(true)) {
        actuallyRelease();
      }
    }

    @Override
    protected void finalize() {
      if (!isReleased.get()) {
        logger.atSevere().log("QuotaManager.Lease has not been released properly");
        close();
      }
    }

    abstract void actuallyRelease();
  }

  static final class EmptyLease implements Lease {

    @Override
    public void release() {}
  }

  static final Lease EMPTY_LEASE = new EmptyLease();

  private static final QuotaManager INSTANCE = initialize();

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private static QuotaManager initialize() {
    try {
      return createLocalQuotaManager();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to create quota manager, do you forget to add runtime_deps like "
              + "\"local_quota_manager\" or \"remote_quota_manager\" if you use "
              + "\"quota_manager_interface_only\"?",
          e);
    }
  }

  private static QuotaManager createLocalQuotaManager() throws ReflectiveOperationException {
    return Class.forName("com.google.devtools.mobileharness.shared.util.quota.LocalQuotaManager")
        .asSubclass(QuotaManager.class)
        .getDeclaredConstructor()
        .newInstance();
  }

  /** Acquires quota with the given key and amount. */
  @CheckReturnValue
  public abstract Lease acquire(QuotaKey key, int amount) throws InterruptedException;

  /** Tries to acquire quota with the given key and amount. */
  @CheckReturnValue
  public abstract Optional<Lease> tryAcquire(QuotaKey key, int amount);
}
