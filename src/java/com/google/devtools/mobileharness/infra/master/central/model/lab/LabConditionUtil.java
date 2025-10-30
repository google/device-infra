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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerConditionOrBuilder;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.time.Duration;
import java.time.Instant;

/** Utils for processing {@link LabServerCondition}. */
public final class LabConditionUtil {

  /**
   * Checks whether the timestamp of a non-MISSING Lab Server is older than lab expiration
   * threshold. If yes, it should be marked as MISSING.
   */
  public static boolean isExpired(LabServerCondition condition) {
    Instant timestamp = Instant.ofEpochMilli(condition.getTimestampMs());
    return !condition.getIsMissing()
        && timestamp
            .plus(Flags.instance().labExpirationThreshold.getNonNull())
            .isBefore(Instant.now());
  }

  /**
   * Checks whether the Lab Server is MISSING and its timestamp is older than removal threshold If
   * yes, it should be totally removed from Master Central DB.
   */
  public static boolean shouldRemove(LabDao labDao) {
    if (labDao.labServerCondition().isPresent()) {
      LabLocator labLocator = labDao.locator();
      LabServerCondition condition = labDao.labServerCondition().get();
      Instant timestamp = Instant.ofEpochMilli(condition.getTimestampMs());
      boolean isEphemeralLab = false;
      return condition.getIsMissing()
          && timestamp
              .plus(
                  isEphemeralLab
                      ? Flags.instance().ephemeralRemovalThreshold.get()
                      : Flags.instance().labRemovalThreshold.getNonNull())
              .isBefore(Instant.now());
    }
    return false;
  }

  /** Reports the detail of the Lab Server condition. */
  public static String getReport(LabServerConditionOrBuilder labServerCondition) {
    return String.format(
        "Heartbeat@%s%s",
        Instant.ofEpochMilli(labServerCondition.getTimestampMs()),
        labServerCondition.getIsMissing() ? "/IsMissing" : "");
  }

  /** Gets the expiration threshold. */
  public static Duration getExpirationThreshold() {
    return Flags.instance().labExpirationThreshold.getNonNull();
  }

  /** Gets the removal threshold. */
  public static Duration getRemovalThreshold() {
    return Flags.instance().labRemovalThreshold.getNonNull();
  }

  private LabConditionUtil() {}
}
