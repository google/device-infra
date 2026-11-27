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

package com.google.devtools.mobileharness.shared.util.file.remote;

import static com.google.common.collect.Comparators.min;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded in-process cache of GCS objects confirmed fresh in one bucket.
 *
 * <p>Entries store monotonic deadlines capped by both the configured cache TTL and the object's
 * remaining cloud cache TTL. Reads and invalidations carry a version stamped before the GCS call so
 * an older in-flight read cannot resurrect an invalidated entry and a stale miss cannot erase a
 * newer write.
 */
abstract class CloudFreshnessCache {

  /** Maximum entries retained per manager. */
  private static final long MAX_SIZE = 50_000L;

  /** Upper bound on cache TTL so nanosecond arithmetic cannot overflow. */
  private static final Duration MAX_TTL = Duration.ofDays(365);

  private static final CloudFreshnessCache DISABLED =
      new CloudFreshnessCache() {
        @Override
        boolean isEnabled() {
          return false;
        }

        @Override
        boolean isFresh(Path gcsFile) {
          return false;
        }

        @Override
        ReadStamp beginRead() {
          return ReadStamp.UNUSED;
        }

        @Override
        void recordFresh(Path gcsFile) {}

        @Override
        void recordFresh(Path gcsFile, Duration remainingCloudTtl, ReadStamp readStamp) {}

        @Override
        void invalidate(Path gcsFile, ReadStamp readStamp) {}
      };

  /** Monotonic clock and version snapshot taken before querying GCS metadata. */
  record ReadStamp(long measuredAtNanos, long version) {
    private static final ReadStamp UNUSED = new ReadStamp(0L, 0L);
  }

  static CloudFreshnessCache create(
      Optional<Duration> cloudCacheTtl, Duration configuredFreshCacheTtl, Ticker ticker) {
    if (cloudCacheTtl.isEmpty()
        || cloudCacheTtl.get().compareTo(Duration.ZERO) <= 0
        || configuredFreshCacheTtl.compareTo(Duration.ZERO) <= 0) {
      return DISABLED;
    }
    Duration entryTtlCap = min(min(configuredFreshCacheTtl, cloudCacheTtl.get()), MAX_TTL);
    return new Active(entryTtlCap, ticker);
  }

  abstract boolean isEnabled();

  abstract boolean isFresh(Path gcsFile);

  abstract ReadStamp beginRead();

  /** Records {@code gcsFile} as freshly written now, valid for up to the cache TTL cap. */
  abstract void recordFresh(Path gcsFile);

  /**
   * Records {@code gcsFile} as fresh from a metadata read started at {@code readStamp}, capped by
   * {@code remainingCloudTtl}.
   */
  abstract void recordFresh(Path gcsFile, Duration remainingCloudTtl, ReadStamp readStamp);

  /**
   * Drops any cached freshness older than {@code readStamp} for {@code gcsFile} and blocks older
   * in-flight reads without erasing newer writes.
   */
  abstract void invalidate(Path gcsFile, ReadStamp readStamp);

  private static final class Active extends CloudFreshnessCache {

    private sealed interface Entry permits Fresh, Invalidated {
      long version();
    }

    private record Fresh(long freshUntilNanos, long version) implements Entry {}

    private record Invalidated(long version) implements Entry {}

    private final Duration entryTtlCap;
    private final Ticker ticker;
    private final AtomicLong versions = new AtomicLong();
    private final Cache<Path, Entry> entries;

    private Active(Duration entryTtlCap, Ticker ticker) {
      this.entryTtlCap = entryTtlCap;
      this.ticker = ticker;
      this.entries =
          CacheBuilder.newBuilder()
              .maximumSize(MAX_SIZE)
              .expireAfterWrite(entryTtlCap)
              .ticker(ticker)
              .build();
    }

    @Override
    boolean isEnabled() {
      return true;
    }

    @Override
    boolean isFresh(Path gcsFile) {
      Entry entry = entries.getIfPresent(gcsFile);
      if (!(entry instanceof Fresh fresh)) {
        return false;
      }
      if (fresh.freshUntilNanos() - ticker.read() > 0) {
        return true;
      }
      // Compare-and-remove so an expired check cannot evict a fresher concurrent update.
      entries.asMap().remove(gcsFile, entry);
      return false;
    }

    @Override
    ReadStamp beginRead() {
      return new ReadStamp(ticker.read(), versions.incrementAndGet());
    }

    @Override
    void recordFresh(Path gcsFile) {
      recordFresh(gcsFile, entryTtlCap, new ReadStamp(ticker.read(), versions.incrementAndGet()));
    }

    @Override
    void recordFresh(Path gcsFile, Duration remainingCloudTtl, ReadStamp readStamp) {
      if (remainingCloudTtl.compareTo(Duration.ZERO) <= 0) {
        return;
      }
      Duration entryTtl = min(entryTtlCap, remainingCloudTtl);
      long freshUntilNanos = readStamp.measuredAtNanos() + entryTtl.toNanos();
      if (freshUntilNanos - ticker.read() <= 0) {
        return;
      }
      entries
          .asMap()
          .compute(
              gcsFile,
              (key, existing) -> {
                if (existing instanceof Invalidated invalidated
                    && readStamp.version() - invalidated.version() < 0) {
                  return existing;
                }
                if (existing instanceof Fresh fresh) {
                  long deadline =
                      freshUntilNanos - fresh.freshUntilNanos() > 0
                          ? freshUntilNanos
                          : fresh.freshUntilNanos();
                  long version =
                      readStamp.version() - fresh.version() > 0
                          ? readStamp.version()
                          : fresh.version();
                  return deadline == fresh.freshUntilNanos() && version == fresh.version()
                      ? existing
                      : new Fresh(deadline, version);
                }
                return new Fresh(freshUntilNanos, readStamp.version());
              });
    }

    @Override
    void invalidate(Path gcsFile, ReadStamp readStamp) {
      long version = readStamp.version();
      entries
          .asMap()
          .compute(
              gcsFile,
              (key, existing) ->
                  existing != null && existing.version() - version > 0
                      ? existing
                      : new Invalidated(version));
    }
  }
}
