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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher;

import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toSecondsAsDouble;

import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.FileTransferEvent.ExecutionType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watcher of a file transfer event. It collects file transfer execution events, and creates reports
 * at last.
 */
public class FileTransferWatcher {

  /** Watched events. */
  private final List<FileTransferEvent> events = Collections.synchronizedList(new ArrayList<>());

  /** Archives {@code event}. */
  public void addEvent(FileTransferEvent event) {
    events.add(event);
  }

  /**
   * @return report string of watched {@code action} events.
   */
  public String report(ExecutionType action) {
    Duration totalDuration = Duration.ZERO;
    long totalFileSize = 0;
    int totalFileCount = 0;

    long cachedFileSize = 0;
    long cachedFileCount = 0;

    for (FileTransferEvent event : events) {
      if (event.type() != action) {
        continue;
      }
      totalFileSize += event.fileSize();
      totalDuration = totalDuration.plus(Duration.between(event.start(), event.end()));
      totalFileCount++;

      if (event.isCached()) {
        cachedFileSize += event.fileSize();
        cachedFileCount++;
      }
    }
    if (cachedFileCount > 0) {
      return String.format(
          "%s %s files (with %s files cached); "
              + "total size: %s (with %s cached); total time: %s; "
              + "speed: %.2f B/s (with cache), %.2f B/s (without cache)",
          action.toString().toLowerCase(Locale.ROOT),
          totalFileCount,
          cachedFileCount,
          StrUtil.getHumanReadableSize(totalFileSize),
          StrUtil.getHumanReadableSize(cachedFileSize),
          totalDuration,
          totalFileSize * 1.0 / toSecondsAsDouble(totalDuration.plusSeconds(1)),
          (totalFileSize - cachedFileSize) * 1.0 / toSecondsAsDouble(totalDuration.plusSeconds(1)));
    } else {
      return String.format(
          "%s %s files; total size: %s; total time: %s; speed: %.2f B/s",
          action.toString().toLowerCase(Locale.ROOT),
          totalFileCount,
          StrUtil.getHumanReadableSize(totalFileSize),
          totalDuration,
          totalFileSize * 1.0 / toSecondsAsDouble(totalDuration.plusSeconds(1)));
    }
  }

  /**
   * @return report string of all watched events.
   */
  public String report() {
    return String.format(
        "\nFileTransfer report:\n  %s\n  %s",
        report(ExecutionType.SEND), report(ExecutionType.GET));
  }

  /**
   * @return file transfer measurement break down by without cache vs total.
   */
  public Map<String, String> getFileTransferMeasurement() {
    Duration sendDurationTotal = Duration.ZERO;
    Duration getDurationTotal = Duration.ZERO;
    Duration sendDurationWithoutCache = Duration.ZERO;
    Duration getDurationWithoutCache = Duration.ZERO;
    long sendFileSizeTotal = 0;
    long getFileSizeTotal = 0;
    long sendFileSizeWithoutCache = 0;
    long getFileSizeWithoutCache = 0;
    int sendFileNum = 0;
    int sendFileNumWithoutCache = 0;
    int getFileNum = 0;
    int getFileNumWithoutCache = 0;

    for (FileTransferEvent event : events) {
      if (event.type() == ExecutionType.SEND) {
        sendFileNum++;
        sendFileSizeTotal += event.fileSize();
        sendDurationTotal = sendDurationTotal.plus(Duration.between(event.start(), event.end()));
        if (!event.isCached()) {
          sendFileNumWithoutCache++;
          sendFileSizeWithoutCache += event.fileSize();
          sendDurationWithoutCache =
              sendDurationWithoutCache.plus(Duration.between(event.start(), event.end()));
        }
      }

      if (event.type() == ExecutionType.GET) {
        getFileNum++;
        getFileSizeTotal += event.fileSize();
        getDurationTotal = getDurationTotal.plus(Duration.between(event.start(), event.end()));
        if (!event.isCached()) {
          getFileNumWithoutCache++;
          getFileSizeWithoutCache += event.fileSize();
          getDurationWithoutCache =
              getDurationWithoutCache.plus(Duration.between(event.start(), event.end()));
        }
      }
    }

    Map<String, String> measurement = new HashMap<>();

    measurement.put("send_file_num", Integer.toString(sendFileNum));
    measurement.put("get_file_num", Integer.toString(getFileNum));
    measurement.put("sent_file_num_without_cache", Integer.toString(sendFileNumWithoutCache));
    measurement.put("get_file_num_without_cache", Integer.toString(getFileNumWithoutCache));
    measurement.put("send_file_size_total", Long.toString(sendFileSizeTotal));
    measurement.put("get_file_size_total", Long.toString(getFileSizeTotal));
    measurement.put(
        "send_time_total", Long.toString(sendDurationTotal.plusSeconds(1).getSeconds()));
    measurement.put("get_time_total", Long.toString(getDurationTotal.getSeconds() + 1));
    measurement.put("send_file_size_without_cache", Long.toString(sendFileSizeWithoutCache));
    measurement.put("get_file_size_without_cache", Long.toString(getFileSizeWithoutCache));
    measurement.put(
        "send_time_without_cache",
        Long.toString(sendDurationWithoutCache.plusSeconds(1).getSeconds()));
    measurement.put(
        "get_time_without_cache",
        Long.toString(getDurationWithoutCache.plusSeconds(1).getSeconds()));

    return measurement;
  }
}
