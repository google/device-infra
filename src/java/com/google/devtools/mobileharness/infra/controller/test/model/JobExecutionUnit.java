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

package com.google.devtools.mobileharness.infra.controller.test.model;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import com.google.devtools.mobileharness.api.model.job.out.Timing;
import com.google.devtools.mobileharness.infra.controller.test.util.JobTimer;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;

/**
 * Data model for a job which contains the job information needed for the job/test executor like lab
 * server process.
 */
@AutoValue
public abstract class JobExecutionUnit {

  public static JobExecutionUnit create(
      JobLocator locator, String driver, Timeout timeout, Timing timing, Dirs dirs) {
    return new AutoValue_JobExecutionUnit(
        locator, driver, timeout, timing, JobTimer.create(timing, timeout), dirs);
  }

  /** Returns the ID and name of the job. */
  public abstract JobLocator locator();

  /** Gets the driver type. */
  public abstract String driver();

  /** Job timeout setting. See go/mh-timing for more detail. */
  public abstract Timeout timeout();

  /** Time records of the job. */
  public abstract Timing timing();

  /** Timer of the job which starts when the job starts and expires when the job expires. */
  public abstract CountDownTimer timer();

  /** Job directory settings. */
  public abstract Dirs dirs();
}
