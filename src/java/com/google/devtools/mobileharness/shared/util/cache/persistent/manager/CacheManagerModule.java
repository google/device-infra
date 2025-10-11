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

package com.google.devtools.mobileharness.shared.util.cache.persistent.manager;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.nio.file.Path;

/** Guice module for {@link CacheManager}. */
final class CacheManagerModule extends AbstractModule {

  private final Path rootCacheDir;
  private final DataSize maxCacheSize;
  private final double trimToRatio;
  private final ListeningExecutorService executorService;

  public CacheManagerModule(
      Path rootCacheDir,
      DataSize maxCacheSize,
      double trimToRatio,
      ListeningExecutorService executorService) {
    this.rootCacheDir = rootCacheDir;
    this.maxCacheSize = maxCacheSize;
    this.trimToRatio = trimToRatio;
    this.executorService = executorService;
  }

  @Override
  protected void configure() {
    bind(Path.class).annotatedWith(Names.named("root_cache_dir")).toInstance(rootCacheDir);
    bind(DataSize.class).annotatedWith(Names.named("max_cache_size")).toInstance(maxCacheSize);
    bind(Double.class).annotatedWith(Names.named("trim_to_ratio")).toInstance(trimToRatio);
    bind(ListeningExecutorService.class)
        .annotatedWith(Names.named("cache_evictor_executor"))
        .toInstance(executorService);
    bind(CacheManager.class).in(Singleton.class);
    bind(CacheEvictor.class).in(Singleton.class);
    bind(CacheScanner.class).in(Singleton.class);
    bind(EvictionPolicy.class).to(LraEvictionPolicy.class).in(Singleton.class);
  }
}
