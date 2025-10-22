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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.DeviceEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LineProcessor to detect single line events from logcat based on supplied {@link
 * DeviceEventConfig}.
 */
public class DeviceEventDetector implements LineProcessor {

  private final ImmutableList<DeviceEventConfig> deviceEventConfigs;

  public DeviceEventDetector(ImmutableList<DeviceEventConfig> deviceEventConfigs) {
    this.deviceEventConfigs = deviceEventConfigs;
  }

  private final List<DeviceEvent> deviceEvents = new ArrayList<>();

  @Override
  public void process(LogcatLine line) {
    for (var config : deviceEventConfigs) {
      processEachEvent(line, config);
    }
  }

  private void processEachEvent(LogcatLine line, DeviceEventConfig config) {
    if (!config.tag.matcher(line.tag()).matches()) {
      return;
    }
    if (config.lineRegex.matcher(line.message()).matches()) {
      deviceEvents.add(new DeviceEvent(config.eventName, line.tag(), line.message()));
    }
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return ImmutableList.copyOf(deviceEvents);
  }

  /** Record class to to hold detection config for different events. */
  public record DeviceEventConfig(String eventName, Pattern tag, Pattern lineRegex) {
    public DeviceEventConfig(String eventName, String tag, String lineRegex) {
      this(eventName, Pattern.compile(tag), Pattern.compile(lineRegex));
    }
  }
}
