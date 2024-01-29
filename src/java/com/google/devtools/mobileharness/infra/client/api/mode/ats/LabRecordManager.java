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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecord;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** The lab record manager. */
public class LabRecordManager extends AbstractScheduledService {

  private static final Duration LAB_MISSING_DELAY = Duration.ofMinutes(10L);

  private final Map<String, LabHistory> labHistories = new ConcurrentHashMap<>();
  private final Clock clock;

  @Inject
  LabRecordManager(Clock clock) {
    this.clock = clock;
  }

  void addLabRecordIfLabInfoChanged(LabInfo labInfo) {
    String hostName = labInfo.getLabLocator().getHostName();
    LabHistory labHistory =
        labHistories.computeIfAbsent(hostName, host -> new LabHistory(host, clock));
    labHistory.addLabRecordIfLabInfoChanged(labInfo);
  }

  ImmutableList<LabRecord> getLabRecords(String hostName) {
    LabHistory labHistory = labHistories.get(hostName);
    return labHistory == null ? ImmutableList.of() : labHistory.getLabRecords();
  }

  @Override
  protected void runOneIteration() {
    for (LabHistory labHistory : labHistories.values()) {
      labHistory.addLabRecordWhenBecomeMissing();
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(/* initialDelay= */ 1, /* delay= */ 1, MINUTES);
  }

  private static class LabHistory {
    private static final int LAB_RECORD_MAX_SIZE = 100;

    @SuppressWarnings("unused")
    private final String hostName;

    private final Clock clock;

    @GuardedBy("itself")
    private final EvictingQueue<LabRecord> labRecordQueue;

    private LabHistory(String hostName, Clock clock) {
      this.hostName = hostName;
      this.clock = clock;
      labRecordQueue = EvictingQueue.create(LAB_RECORD_MAX_SIZE);
    }

    private ImmutableList<LabRecord> getLabRecords() {
      synchronized (labRecordQueue) {
        return ImmutableList.copyOf(labRecordQueue);
      }
    }

    private void addLabRecordIfLabInfoChanged(LabInfo labInfo) {
      synchronized (labRecordQueue) {
        LabRecord lastLabRecord = labRecordQueue.peek();
        if (lastLabRecord == null || isLabInfoChanged(lastLabRecord.getLabInfo(), labInfo)) {
          internalAddLabRecord(labInfo);
        }
      }
    }

    private void addLabRecordWhenBecomeMissing() {
      synchronized (labRecordQueue) {
        LabRecord lastLabRecord = labRecordQueue.peek();
        if (lastLabRecord == null) {
          return;
        }
        Instant lastUpdateTime = TimeUtils.toJavaInstant(lastLabRecord.getTimestamp());
        Instant now = clock.instant();
        if (now.isAfter(lastUpdateTime.plus(LAB_MISSING_DELAY))
            && lastLabRecord.getLabInfo().getLabStatus() != LabStatus.LAB_MISSING) {
          LabInfo.Builder newLabInfo = lastLabRecord.getLabInfo().toBuilder();
          newLabInfo.setLabStatus(LabStatus.LAB_MISSING);
          internalAddLabRecord(newLabInfo.build());
        }
      }
    }

    @GuardedBy("labRecordQueue")
    private void internalAddLabRecord(LabInfo labInfo) {
      Instant now = clock.instant();
      labRecordQueue.offer(
          LabRecord.newBuilder()
              .setTimestamp(TimeUtils.toProtoTimestamp(now))
              .setLabInfo(labInfo)
              .build());
    }

    private static boolean isLabInfoChanged(LabInfo previousLabInfo, LabInfo newLabInfo) {
      if (previousLabInfo.getLabStatus() != newLabInfo.getLabStatus()) {
        return true;
      }
      // TODO: Not all feature change need to trigger adding new record.
      if (!previousLabInfo.getLabServerFeature().equals(newLabInfo.getLabServerFeature())) {
        return true;
      }
      return false;
    }
  }
}
