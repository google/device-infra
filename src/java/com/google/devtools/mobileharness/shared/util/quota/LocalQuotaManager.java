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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import com.google.errorprone.annotations.Keep;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Local implementation of {@link QuotaManager}. */
class LocalQuotaManager extends QuotaManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private class LocalLease extends BaseLease {

    private final QuotaKey key;
    private final int amount;

    private LocalLease(QuotaKey key, int amount) {
      this.key = key;
      this.amount = amount;
    }

    @Override
    void actuallyRelease() {
      semaphoresSupplier.get().get(key).release(amount);
      logger.atInfo().log("Released amount %d on QuotaKey %s.", amount, key);
    }
  }

  private final Supplier<ImmutableMap<QuotaKey, Semaphore>> semaphoresSupplier;

  @Keep
  LocalQuotaManager() {
    this(
        () ->
            ImmutableMap.of(
                QuotaKey.ADB_PUSH_LARGE_FILE,
                Flags.instance().maxConcurrentAdbPushLargeFile.getNonNull(),
                QuotaKey.UNZIP_LARGE_FILE,
                Flags.instance().maxConcurrentUnzipLargeFile.getNonNull(),
                QuotaKey.FASTBOOT_FLASH_DEVICE,
                Flags.instance().maxConcurrentFlashDevice.getNonNull(),
                QuotaKey.TF_CONTAINER_INSTANCE,
                1));
  }

  @VisibleForTesting
  LocalQuotaManager(Supplier<Map<QuotaKey, Integer>> quotasSupplier) {
    this.semaphoresSupplier =
        Suppliers.memoize(
            () ->
                quotasSupplier.get().entrySet().stream()
                    .collect(
                        ImmutableMap.toImmutableMap(
                            Entry::getKey, e -> new Semaphore(e.getValue()))));
  }

  @Override
  public Lease acquire(QuotaKey key, int amount) throws InterruptedException {
    if (semaphoresSupplier.get().containsKey(key)) {
      semaphoresSupplier.get().get(key).acquire(amount);
      return new LocalLease(key, amount);
    }
    return EMPTY_LEASE;
  }

  @Override
  public Optional<Lease> tryAcquire(QuotaKey key, int amount) {
    if (semaphoresSupplier.get().containsKey(key)) {
      if (semaphoresSupplier.get().get(key).tryAcquire(amount)) {
        return Optional.of(new LocalLease(key, amount));
      }
      return Optional.empty();
    }
    return Optional.of(EMPTY_LEASE);
  }
}
