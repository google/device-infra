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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidNetworkActivityLoggingDecoratorSpec;
import javax.inject.Inject;

/**
 * Records network events of applications on the device.
 *
 * <p>The decorator uses the MTaaS DeviceAdmin app to:
 *
 * <ol>
 *   <li>Enable network logging at the beginning of the test
 *   <li>Force a log dump at the end of the test
 *   <li>Record network connection events of type {@code TcpConnectEvent} to a file named {@code
 *       network_events.dpb}
 * </ol>
 *
 * <p>See https://developer.android.com/work/dpc/logging for more details about the network activity
 * logging feature.
 */
public class AndroidNetworkActivityLoggingDecorator extends BaseDecorator
    implements SpecConfigable<AndroidNetworkActivityLoggingDecoratorSpec> {

  @Inject
  AndroidNetworkActivityLoggingDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidNetworkActivityLoggingDecoratorSpec unused =
        testInfo.jobInfo().combinedSpec(this, deviceId);
    // TODO Implement the decorator
    getDecorated().run(testInfo);
  }
}
