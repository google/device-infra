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

package com.google.devtools.mobileharness.shared.util.cache.persistent;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.ImmutableTypeParameter;
import java.nio.file.Path;
import java.time.InstantSource;
import javax.annotation.Nullable;

/** Builder for {@link PersistentCache}. */
public final class CacheBuilder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Path rootPersistentDir;
  private final LocalFileUtil localFileUtil;
  private final InstantSource instantSource;

  private static class SingletonHolder {
    private static final CacheBuilder instance = createInstance();

    @Nullable
    private static CacheBuilder createInstance() {
      if (!Flags.instance().enablePersistentCache.getNonNull()) {
        return null;
      }
      String rootDir = Flags.instance().persistentCacheDir.get();
      if (rootDir == null || !isRootDirValid(rootDir)) {
        return null;
      }
      logger.atInfo().log("Creating PersistentCache with rootDir: %s", rootDir);
      return new CacheBuilder(Path.of(rootDir), new LocalFileUtil(), InstantSource.system());
    }

    private static boolean isRootDirValid(String rootDir) {
      LocalFileUtil localFileUtil = new LocalFileUtil();
      return localFileUtil.isDirExist(rootDir);
    }
  }

  private CacheBuilder(
      Path rootPersistentDir, LocalFileUtil localFileUtil, InstantSource instantSource) {
    this.rootPersistentDir = rootPersistentDir;
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
  }

  /** Returns the singleton instance of {@link CacheBuilder}. */
  @Nullable
  public static CacheBuilder getInstance() {
    return SingletonHolder.instance;
  }

  /** Builds a {@link PersistentCache} with the given {@link CacheLoader}. */
  public <@ImmutableTypeParameter K> PersistentCache<K> build(CacheLoader<K> cacheLoader) {
    return new PersistentCache<>(rootPersistentDir, localFileUtil, instantSource, cacheLoader);
  }
}
