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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.collect.ImmutableSet;

/**
 * Constants for sharding inherited from {@code
 * google3/third_party/py/multitest_transport/test_scheduler/sharding_strategies.py}
 */
public final class ShardConstants {

  // Modules that should be shard to several commands for execution efficiency
  public static final ImmutableSet<String> SHARD_MODULES = ImmutableSet.of("CtsDeqpTestCases");

  // Modules that should be shard to several commands for execution efficiency
  public static final ImmutableSet<String> LARGE_MODULES =
      ImmutableSet.of(
          "CtsAutoFillServiceTestCases",
          "CtsMediaStressTestCases",
          "CtsSecurityTestCases",
          "CtsVideoTestCases",
          "CtsKeystoreWycheproofTestCases",
          "CtsCameraTestCases",
          "CtsWindowManagerDeviceTestCases",
          "CtsLibcoreOjTestCases",
          "CtsInstallHostTestCases");

  private ShardConstants() {}
}
