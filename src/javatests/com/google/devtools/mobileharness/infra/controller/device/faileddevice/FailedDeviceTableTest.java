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

package com.google.devtools.mobileharness.infra.controller.device.faileddevice;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.MoreExecutors;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link FailedDeviceTable}. */
@RunWith(JUnit4.class)
public class FailedDeviceTableTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private ScheduledExecutorService service;

  private static final String DEVICE_CONTROL_ID = "device_control_id";
  private static final int DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL = 3;

  private FailedDeviceTable table;

  @Before
  public void setUp() {
    table = new FailedDeviceTable(MoreExecutors.listeningDecorator(service));
  }

  @Test
  public void addFailedDeviceId() {
    assertThat(table.getFailedDeviceIds()).isEmpty();
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    assertThat(table.getFailedDeviceIds()).hasSize(1);
  }

  @Test
  public void removeFailedDeviceId() {
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    assertThat(table.getFailedDeviceIds()).hasSize(1);
    table.remove(DEVICE_CONTROL_ID);
    assertThat(table.getFailedDeviceIds()).isEmpty();
  }

  @Test
  public void removeMultipleAdds() {
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    assertThat(table.getFailedDeviceIds()).hasSize(1);
    table.remove(DEVICE_CONTROL_ID);
    assertThat(table.getFailedDeviceIds()).isEmpty();
  }

  @Test
  public void oneAddDoesNotMakeADeviceFailed() {
    table.add(DEVICE_CONTROL_ID);
    assertThat(table.has(DEVICE_CONTROL_ID)).isFalse();
  }

  @Test
  public void hasFailedDeviceId() {
    for (int i = 0; i < DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL; i++) {
      table.add(DEVICE_CONTROL_ID);
    }
    assertThat(table.has(DEVICE_CONTROL_ID)).isTrue();
  }

  @Test
  public void scheduleCleanUp() {
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    table.add(DEVICE_CONTROL_ID);
    verify(service, times(1))
        .scheduleWithFixedDelay(
            any(),
            eq(FailedDeviceTable.CLEANUP_INTERVAL.toMillis()),
            eq(FailedDeviceTable.CLEANUP_INTERVAL.toMillis()),
            eq(MILLISECONDS));
  }

  // This access should be guarded by 'lock', which could not be resolved
  @SuppressWarnings("GuardedBy")
  @Test
  public void doesNotCleanUpFreshEntriesWith1Cnt() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(Clock.systemUTC().instant(), 1));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
  }

  // This access should be guarded by 'lock', which could not be resolved
  @SuppressWarnings("GuardedBy")
  @Test
  public void doesNotCleanUpFreshEntriesWith2Cnts() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(
            Clock.systemUTC().instant(), DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL - 1));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
  }

  // This access should be guarded by 'lock', which could not be resolved
  @SuppressWarnings("GuardedBy")
  @Test
  public void doesNotCleanUpFreshFailedDeviceEntries() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(
            Clock.systemUTC().instant(), DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
  }

  // This access should be guarded by 'lock', which could not be resolved
  @SuppressWarnings("GuardedBy")
  @Test
  public void cleanUp() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID, FailedDeviceTable.FailedDeviceEntryInfo.of(Instant.MIN, 1));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).doesNotContainKey(DEVICE_CONTROL_ID);
  }

  @SuppressWarnings("GuardedBy")
  @Test
  public void cleanUpOldEntriesWith2Cnts() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(
            Instant.MIN, DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL - 1));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).doesNotContainKey(DEVICE_CONTROL_ID);
  }

  @SuppressWarnings("GuardedBy")
  @Test
  public void cleanUpOldEntriesWith3Cnts() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(
            Instant.MIN, DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    table.cleanUp();
    assertThat(table.failedDevices).doesNotContainKey(DEVICE_CONTROL_ID);
  }

  // This access should be guarded by 'lock', which could not be resolved
  @SuppressWarnings("GuardedBy")
  @Test
  public void hasDoesCleansUp() {
    table.failedDevices.put(
        DEVICE_CONTROL_ID,
        FailedDeviceTable.FailedDeviceEntryInfo.of(
            Instant.MIN, DEFAULT_MAX_INIT_FAILURES_BEFORE_FAIL));
    assertThat(table.failedDevices).containsKey(DEVICE_CONTROL_ID);
    assertThat(table.has(DEVICE_CONTROL_ID)).isFalse();
    assertThat(table.failedDevices).doesNotContainKey(DEVICE_CONTROL_ID);
  }
}
