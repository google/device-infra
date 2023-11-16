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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import java.util.Optional;

/**
 * A general container class for a document that contains the result of a single Mobly test.
 *
 * <p>This class should mirror the Mobly class at {@code records.py#TestResultRecord}. Any changes
 * made to the Mobly class should be mirrored here carefully.
 */
@AutoValue
public abstract class MoblyTestEntry implements MoblyYamlDocEntry {

  @Override
  public Type getType() {
    return Type.RECORD;
  }

  public abstract String getTestName();

  public abstract String getTestClass();

  /** Result of test given from Mobly */
  public abstract MoblyResult getResult();

  /** Start time of test in milliseconds since epoch */
  public abstract Optional<Long> getBeginTime();

  /** End time of test in milliseconds since epoch */
  public abstract Optional<Long> getEndTime();

  public static Builder builder() {
    return new com.google.devtools.mobileharness.infra.ats.console.result.mobly
        .AutoValue_MoblyTestEntry.Builder();
  }

  /** MoblyTestEntry Builder class. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestName(String value);

    public abstract Builder setTestClass(String value);

    public abstract Builder setBeginTime(long value);

    public abstract Builder setEndTime(long value);

    public abstract Builder setResult(MoblyResult value);

    public abstract MoblyTestEntry build();
  }
}
