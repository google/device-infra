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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class XtsTradefedRuntimeInfoTest {

  @Test
  public void xtsTradefedRuntimeInfo_convertString() {
    ImmutableList<TradefedInvocation> invocations =
        ImmutableList.of(
            new TradefedInvocation(false, ImmutableList.of("left", "123", "abc"), "failed", ""),
            new TradefedInvocation(true, ImmutableList.of("right", "11"), "passed", ""),
            new TradefedInvocation(true, ImmutableList.of("top"), "", ""),
            new TradefedInvocation(true, ImmutableList.of(), "passed", ""),
            new TradefedInvocation(
                false, ImmutableList.of("bottom"), "failed", "example error message"));
    XtsTradefedRuntimeInfo info =
        new XtsTradefedRuntimeInfo(invocations, Instant.ofEpochMilli(1234567890L));
    XtsTradefedRuntimeInfo infoWithoutInvocations =
        new XtsTradefedRuntimeInfo(ImmutableList.of(), Instant.ofEpochMilli(1234567890L));

    assertThat(XtsTradefedRuntimeInfo.decodeFromString(info.encodeToString())).isEqualTo(info);
    assertThat(XtsTradefedRuntimeInfo.decodeFromString(infoWithoutInvocations.encodeToString()))
        .isEqualTo(infoWithoutInvocations);
  }

  @Test
  public void tradefedInvocation_convertString() {
    TradefedInvocation invocation =
        new TradefedInvocation(false, ImmutableList.of("left", "123", "abc"), "failed", "");
    TradefedInvocation invocationWithoutStatus =
        new TradefedInvocation(true, ImmutableList.of("left", "123", "abc", "efg"), "", "");
    TradefedInvocation invocationWithoutDevices =
        new TradefedInvocation(false, ImmutableList.of(), "failed", "");
    TradefedInvocation invocationWithErrorMessage =
        new TradefedInvocation(
            false, ImmutableList.of("left", "123", "abc"), "failed", "example error message");
    TradefedInvocation emptyInvocation = new TradefedInvocation(true, ImmutableList.of(), "", "");

    assertThat(TradefedInvocation.decodeFromString(invocation.encodeToString()))
        .isEqualTo(invocation);
    assertThat(TradefedInvocation.decodeFromString(invocationWithoutStatus.encodeToString()))
        .isEqualTo(invocationWithoutStatus);
    assertThat(TradefedInvocation.decodeFromString(invocationWithoutDevices.encodeToString()))
        .isEqualTo(invocationWithoutDevices);
    assertThat(TradefedInvocation.decodeFromString(invocationWithErrorMessage.encodeToString()))
        .isEqualTo(invocationWithErrorMessage);
    assertThat(TradefedInvocation.decodeFromString(emptyInvocation.encodeToString()))
        .isEqualTo(emptyInvocation);
  }
}
