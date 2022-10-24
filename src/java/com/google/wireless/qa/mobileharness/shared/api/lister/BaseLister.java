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

package com.google.wireless.qa.mobileharness.shared.api.lister;

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;

/**
 * DEPRECATED. For adapting the {@link Lister} implementation using the old {@link
 * com.google.wireless.qa.mobileharness.shared.api.job.JobInfo} data model only.
 */
@Deprecated
public abstract class BaseLister implements Lister {

  @Override
  public final List<String> listTests(JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException {
    return deprecatedListTests(
        new com.google.wireless.qa.mobileharness.shared.api.job.JobInfo(jobInfo));
  }

  /**
   * @deprecated Uses {@link #listTests(JobInfo)} instead.
   */
  @Deprecated
  public abstract List<String> deprecatedListTests(
      com.google.wireless.qa.mobileharness.shared.api.job.JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException;
}
