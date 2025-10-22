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

package com.google.devtools.mobileharness.platform.android.logcat;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.DeviceEventDetector.DeviceEventConfig;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.DeviceEvent;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceEventDetectorTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";

  private static final String DEVICE_EVENTS_LOG_FILE = "device_events.txt";

  private DeviceEventDetector deviceEventDetector;

  @Before
  public void setUp() {
    var powerButtonEventConfig =
        new DeviceEventConfig(
            "POWER_BUTTON_PRESSED", "PowerManagerService", "Going to sleep due to power_button .+");
    var networkAccessBlockedEventConfig =
        new DeviceEventConfig("NETWORK_ACCESS_BLOCKED", "resolv", ".*network access blocked");
    var networkDnsWriteError =
        new DeviceEventConfig(
            "DNS_WRITE_ERROR", "resolv", ".*Error writing DNS result to client uid .*");

    var networkProbeError =
        new DeviceEventConfig(
            "NETWORK_PROBE_ERROR",
            "^NetworkMonitor/.*",
            "^PROBE_.* Probe failed with exception .*");

    deviceEventDetector =
        new DeviceEventDetector(
            ImmutableList.of(
                powerButtonEventConfig,
                networkAccessBlockedEventConfig,
                networkDnsWriteError,
                networkProbeError));
  }

  @Test
  public void process_detectsEvents() throws IOException {
    String crashLogPath =
        RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + DEVICE_EVENTS_LOG_FILE);
    List<String> lines = Files.readAllLines(Path.of(crashLogPath));

    for (String line : lines) {
      deviceEventDetector.process(LogcatParser.parse(line).get());
    }

    assertThat(deviceEventDetector.getEvents()).hasSize(4);
    assertThat(
            deviceEventDetector.getEvents().stream()
                .map(logcatEvent -> ((DeviceEvent) logcatEvent).eventName()))
        .containsExactly(
            "NETWORK_ACCESS_BLOCKED",
            "DNS_WRITE_ERROR",
            "NETWORK_PROBE_ERROR",
            "POWER_BUTTON_PRESSED")
        .inOrder();
  }

  @Test
  public void process_noEvents_returnEmpty() {
    var lines =
        ImmutableList.of(
            "10-13 12:54:02.123  1234  5678 I NotActivityManager: Some message",
            "10-13 12:54:03.123  4321  8765 E ActivityManager: Some other error");
    for (String line : lines) {
      deviceEventDetector.process(LogcatParser.parse(line).get());
    }

    assertThat(deviceEventDetector.getEvents()).isEmpty();
  }
}
