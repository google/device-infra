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

package com.google.wireless.qa.mobileharness.shared.comm.message;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.concurrent.GuardedBy;

/** Cacheable test message handler for caching test messages before posting or forwarding them. */
public abstract class CacheableTestMessageHandler extends MobileHarnessAutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Object cacheTestMessageLock = new Object();

  private final ExecutorService cachedTestMessageExecutor;

  @GuardedBy("cacheTestMessageLock")
  private final List<TestMessageInfo> cachedTestMessages = new ArrayList<>();

  @GuardedBy("cacheTestMessageLock")
  private boolean enableCache = true;

  protected CacheableTestMessageHandler(ExecutorService executorService) {
    this.cachedTestMessageExecutor = executorService;
  }

  /** Actually handles a test message. */
  protected abstract void handleTestMessage(TestMessageInfo testMessageInfo);

  /**
   * Submits a test message.
   *
   * <p>The message will be {@linkplain #handleTestMessage handled}:
   *
   * <ul>
   *   <li>Directly if the cache is disabled now.
   *   <li>When {@link #disableAndHandleCache()} is invoked if the cache is enabled now.
   * </ul>
   */
  protected final void submitTestMessage(TestMessageInfo testMessageInfo) {
    boolean cached;
    synchronized (cacheTestMessageLock) {
      cached = enableCache;
      if (cached) {
        cachedTestMessages.add(testMessageInfo);
      }
    }
    if (cached) {
      logger.atInfo().log("Cache test message %s", testMessageInfo);
    } else {
      handleTestMessage(testMessageInfo);
    }
  }

  @CanIgnoreReturnValue
  public Future<?> asyncDisableAndHandleCache() {
    return cachedTestMessageExecutor.submit(this::disableAndHandleCache);
  }

  @Override
  public void close() {
    cachedTestMessageExecutor.shutdown();
  }

  private void disableAndHandleCache() {
    ImmutableList<TestMessageInfo> cachedTestMessages;
    synchronized (cacheTestMessageLock) {
      enableCache = false;
      cachedTestMessages = ImmutableList.copyOf(this.cachedTestMessages);
      this.cachedTestMessages.clear();
    }
    cachedTestMessages.forEach(
        testMessageInfo -> {
          logger.atInfo().log("Handle cached test message %s", testMessageInfo);
          handleTestMessage(testMessageInfo);
        });
    logger.atFine().log("All cached test messages have been handled");
  }
}
