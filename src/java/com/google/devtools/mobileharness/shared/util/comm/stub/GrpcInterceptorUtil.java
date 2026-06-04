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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.system.LoasUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.time.Duration;

/** Utility class for creating gRPC client interceptors. */
public class GrpcInterceptorUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Supplier<GrpcInterceptorUtil> defaultInstanceSupplier =
      Suppliers.memoize(() -> new GrpcInterceptorUtil(new SystemUtil(), LoasUtil.getInstance()));

  public static GrpcInterceptorUtil getInstance() {
    return defaultInstanceSupplier.get();
  }

  private final SystemUtil systemUtil;
  private final LoasUtil loasUtil;

  private final LoadingCache<String, GoogleCredentials> corpGoogleCredentialsCache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(Duration.ofHours(1))
          .build(
              new CacheLoader<String, GoogleCredentials>() {
                @Override
                public GoogleCredentials load(String user) {
                  return createCorpGoogleCredentials(user);
                }
              });

  GrpcInterceptorUtil(SystemUtil systemUtil, LoasUtil loasUtil) {
    this.systemUtil = systemUtil;
    this.loasUtil = loasUtil;
  }
}
