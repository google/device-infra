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

package com.google.wireless.qa.mobileharness.shared.controller.stat;

import com.google.common.flogger.FluentLogger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Statistic data of the a list of lab servers. */
public class StatManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Statistic of all the labs. Using lab IP as the map key. */
  private final ConcurrentMap<String, LabStat> labStats = new ConcurrentHashMap<>();

  /** Singleton holder for lazy initialization. */
  private static class SingletonHolder {
    private static final StatManager singleton;

    static {
      singleton = new StatManager();
    }
  }

  /** Return the singleton instance of this class. */
  public static StatManager getInstance() {
    return SingletonHolder.singleton;
  }

  private StatManager() {
    // Does nothing.
  }

  /**
   * Gets {@code LabStat} according to the lab IP. If not exists, creates one.
   *
   * @return the existing or newly-created {@code LabStat} of the device
   */
  public LabStat getOrCreateLabStat(String labIp) {
    LabStat newLabStat = new LabStat();
    LabStat labStat = labStats.putIfAbsent(labIp, newLabStat);
    if (labStat == null) {
      logger.atInfo().log("New LabStat created for %s", labIp);
      labStat = newLabStat;
    }
    return labStat;
  }
}
