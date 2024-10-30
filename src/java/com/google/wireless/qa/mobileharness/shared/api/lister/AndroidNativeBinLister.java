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

import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidNativeBin;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test lister for {@code AndroidNativeBin} driver.
 *
 * @author jinrui@google.com (Ray Sun)
 */
public class AndroidNativeBinLister implements Lister {
  @Override
  public List<String> listTests(JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> binPaths = jobInfo.files().get(AndroidNativeBin.TAG_BIN);
    List<String> results = new ArrayList<>(binPaths.size());
    for (String binPath : binPaths) {
      results.add(new File(binPath).getName());
    }
    return results;
  }
}
