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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Optional;

/** Test runner which does actual Mobile Harness test logic. */
public interface DirectTestRunner extends TestRunner {

  /** Event scopes of test runner. */
  enum EventScope {

    /** For this specific runner, for Mobile Harness framework logic. */
    CLASS_INTERNAL,

    /** For global Mobile Harness framework logic. */
    GLOBAL_INTERNAL,

    /** For some special internal plugins which needs to be executed before API/JAR plugins. */
    INTERNAL_PLUGIN,

    /** For all the tests in the same job, for the plugins added via Client API. */
    API_PLUGIN,

    /** For this specific test, for "xx_plugin_jar". */
    JAR_PLUGIN,

    /** For test messages in this specific test. */
    TEST_MESSAGE,
  }

  /** Registers a subscriber for the test events in the given scope. */
  void registerTestEventSubscriber(Object subscriber, EventScope scope);

  /**
   * Gets a snapshot of device features of the current test. Please note that it just returns a
   * snapshot instead of the real-time device features. When the snapshot is updated depends on
   * which phase this the is in. For example, for local test runner, after driver/decorator stack
   * finishes and before the test status is set to DONE, the snapshot will be updated once.
   */
  Optional<List<DeviceFeature>> getDeviceFeatures();

  /** Gets the detail of the current test. */
  TestInfo getTestInfo();

  /** Gets the test message poster of the test. */
  TestMessagePoster getTestMessagePoster();

  /**
   * Gets the plugin loading result of the test.
   *
   * @since MH lab server 4.31.0 and MH client 4.163.0 (local test runner only)
   */
  default ListenableFuture<PluginLoadingResult> getPluginLoadingResult() {
    // TODO: Implements the method.
    throw new UnsupportedOperationException();
  }
}
