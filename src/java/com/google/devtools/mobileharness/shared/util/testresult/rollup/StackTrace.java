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

package com.google.devtools.mobileharness.shared.util.testresult.rollup;

import com.google.auto.value.AutoValue;

/** Data class representing a traceback of an execution exception/error. */
@AutoValue
public abstract class StackTrace {
  public abstract String exception();

  public static StackTrace create(String exception) {
    return builder().setException(exception).build();
  }

  public static Builder builder() {
    return new AutoValue_StackTrace.Builder();
  }

  /** Builder for {@link StackTrace}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setException(String exception);

    public abstract StackTrace build();
  }
}
