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

package com.google.devtools.mobileharness.api.model.job;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.Beta;
import com.google.devtools.mobileharness.api.model.proto.Job;
import java.util.UUID;

/** Locator of a job. */
@Beta
@AutoValue
public abstract class JobLocator {
  public abstract String id();

  public abstract String name();

  /**
   * Creates a locator for a job.
   *
   * @param id job ID
   * @param name job name
   */
  public static JobLocator of(String id, String name) {
    return new AutoValue_JobLocator(id, name);
  }

  /**
   * Creates a locator for a job.
   *
   * @param name job name
   */
  public static JobLocator of(String name) {
    return of(UUID.randomUUID().toString(), name);
  }

  /** Creates a locator for a job. */
  public static JobLocator of(Job.JobLocator proto) {
    return of(proto.getId(), proto.getName());
  }

  @Override
  @Memoized
  public String toString() {
    return id();
  }

  @Memoized
  public Job.JobLocator toProto() {
    return Job.JobLocator.newBuilder().setId(id()).setName(name()).build();
  }
}
