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
import com.google.devtools.mobileharness.api.model.proto.Test;

/** Locator of a test. */
@Beta
@AutoValue
public abstract class TestLocator {
  public abstract String id();

  public abstract String name();

  public abstract JobLocator jobLocator();

  /**
   * Creates a locator for a test.
   *
   * @param id test ID
   * @param name test name
   * @param jobLocator locator of the belonging job
   */
  public static TestLocator of(String id, String name, JobLocator jobLocator) {
    return new AutoValue_TestLocator(id, name, jobLocator);
  }

  /** Creates a locator for a test. */
  public static TestLocator of(Test.TestLocator proto) {
    return new AutoValue_TestLocator(
        proto.getId(), proto.getName(), JobLocator.of(proto.getJobLocator()));
  }

  @Override
  @Memoized
  public String toString() {
    return String.format("%s@%s", id(), jobLocator());
  }

  @Memoized
  public Test.TestLocator toProto() {
    return Test.TestLocator.newBuilder()
        .setId(id())
        .setName(name())
        .setJobLocator(jobLocator().toProto())
        .build();
  }
}
