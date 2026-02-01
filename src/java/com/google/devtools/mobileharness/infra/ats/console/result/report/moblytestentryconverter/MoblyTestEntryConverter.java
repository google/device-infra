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

package com.google.devtools.mobileharness.infra.ats.console.result.report.moblytestentryconverter;

import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;

/** Converter to convert {@link MoblyTestEntry} to {@link Test}. */
public interface MoblyTestEntryConverter {

  /**
   * Converts a given {@link MoblyTestEntry} to a {@link Test} report entry.
   *
   * @param testEntry the Mobly test entry to convert
   * @return the converted {@link Test} report entry
   */
  Test convert(MoblyTestEntry testEntry);
}
