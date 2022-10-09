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

package com.google.wireless.qa.mobileharness.shared.log;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.logging.Level;

/**
 * Mobile Harness log collector for collecting logs to job, test or other components.
 *
 * <p><b>How to get an instance</b>: You can get a {@link LogCollector} by calling {@linkplain
 * com.google.wireless.qa.mobileharness.shared.model.job.TestInfo testInfo/jobInfo}{@linkplain
 * com.google.wireless.qa.mobileharness.shared.model.job.TestInfo#log() .log()}.
 *
 * <p><b>How to test</b>: You can test it by {@linkplain
 * com.google.wireless.qa.mobileharness.shared.log.testing.FakeLogCollector FakeLogCollector} in
 * unit tests.
 *
 * @see com.google.wireless.qa.mobileharness.shared.log.testing.FakeLogCollector
 */
public interface LogCollector<Api extends LoggingApi<Api>> {

  /** Returns a fluent logging API appropriate for the specified log level. */
  @CheckReturnValue
  Api at(Level level);

  /** A convenience method for {@linkplain #at(Level) at}({@link Level#INFO}). */
  @CheckReturnValue
  default Api atInfo() {
    return at(Level.INFO);
  }

  /** A convenience method for {@linkplain #at(Level) at}({@link Level#WARNING}). */
  @CheckReturnValue
  default Api atWarning() {
    return at(Level.WARNING);
  }
}
