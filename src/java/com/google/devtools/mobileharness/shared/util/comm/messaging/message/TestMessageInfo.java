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

package com.google.devtools.mobileharness.shared.util.comm.messaging.message;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/** Internal test message data model for MH messaging framework. */
@AutoValue
public abstract class TestMessageInfo {

  public static TestMessageInfo of(
      String rootTestId,
      Map<String, String> message,
      List<String> subTestIdChain,
      boolean isRemote) {
    return new AutoValue_TestMessageInfo(
        rootTestId, ImmutableMap.copyOf(message), ImmutableList.copyOf(subTestIdChain), isRemote);
  }

  /** Root test ID. */
  public abstract String rootTestId();

  /** Message to send. */
  public abstract ImmutableMap<String, String> message();

  /** Test ID chain from root test to destination sub test. */
  public abstract ImmutableList<String> subTestIdChain();

  /**
   * Whether the message is a remote message which is forwarded from another MH component (e.g., MH
   * client or MH lab server).
   */
  public abstract boolean isRemote();
}
