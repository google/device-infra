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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil;
import java.util.concurrent.ExecutionException;

/** A provider for {@link TestSuiteInfo}. */
public final class TestSuiteInfoProvider {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @AutoValue
  abstract static class CacheKey {
    abstract String xtsRootDir();

    abstract String xtsType();

    static CacheKey create(String xtsRootDir, String xtsType) {
      return new AutoValue_TestSuiteInfoProvider_CacheKey(xtsRootDir, xtsType);
    }
  }

  private static final LoadingCache<CacheKey, TestSuiteInfo> cache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .build(
              new CacheLoader<CacheKey, TestSuiteInfo>() {
                @Override
                public TestSuiteInfo load(CacheKey key) {
                  logger.atFine().log(
                      "Creating %s instance with params [xts root dir: %s, xts type: %s]",
                      TestSuiteInfo.class.getSimpleName(), key.xtsRootDir(), key.xtsType());
                  return new TestSuiteInfo(key.xtsRootDir(), key.xtsType(), new JarFileUtil());
                }
              });

  /**
   * Gets the {@link TestSuiteInfo} for the given xTS root directory and xTS type. Cache is used to
   * avoid creating the same test suite info multiple times.
   *
   * @param xtsRootDir the xTS root directory
   * @param xtsType the xTS type
   * @return the {@link TestSuiteInfo} for the given xTS root directory and xTS type
   * @throws IllegalStateException if there is an error loading the test suite info
   */
  public static TestSuiteInfo getTestSuiteInfo(String xtsRootDir, String xtsType) {
    try {
      return cache.get(CacheKey.create(xtsRootDir, xtsType));
    } catch (ExecutionException e) {
      throw new IllegalStateException(
          String.format(
              "Error loading test suite info for xts root dir \"%s\" and xts type \"%s\"",
              xtsRootDir, xtsType),
          e);
    }
  }

  private TestSuiteInfoProvider() {}
}
