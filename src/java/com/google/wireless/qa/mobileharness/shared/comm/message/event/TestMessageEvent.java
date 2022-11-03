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

package com.google.wireless.qa.mobileharness.shared.comm.message.event;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.wireless.qa.mobileharness.shared.controller.event.DeviceCheckedTestEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.util.Map;

/**
 * Event that signals a message sent to a test.
 *
 * @see <a href="http://go/mh-messaging">MH Messaging</a>
 */
public class TestMessageEvent extends TestEvent implements DeviceCheckedTestEvent {

  private final TestMessageInfo testMessageInfo;

  public TestMessageEvent(
      TestMessageInfo testMessageInfo,
      TestInfo testInfo,
      Allocation allocation,
      DeviceInfo deviceInfo) {
    super(testInfo, allocation, checkNotNull(deviceInfo));
    this.testMessageInfo = testMessageInfo;
  }

  /**
   * Gets the received message.
   *
   * @return the received message. It is immutable and will not be {@code null}.
   */
  public Map<String, String> getMessage() {
    return testMessageInfo.message();
  }

  /**
   * Returns whether the message is a remote message which is forwarded from another Mobile Harness
   * component like client or lab server.
   */
  public boolean isRemote() {
    return testMessageInfo.isRemote();
  }

  /** For internal use only. */
  public TestMessageInfo getTestMessageInfo() {
    return testMessageInfo;
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return deviceInfo;
  }
}
