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

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;

/** Lister for AndroidTestLoopTest driver. */
public class AndroidTestLoopTestLister implements Lister {

  @Override
  public List<String> listTests(JobInfo jobInfo) {
    // Test loop scenarios could be sharded across multiple devices, but currently this is not a
    // requirement.
    return ImmutableList.of(jobInfo.locator().getName());
  }
}
