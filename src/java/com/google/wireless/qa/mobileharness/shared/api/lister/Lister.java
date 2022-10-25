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
 * Test lister for generating the tests of a job when user doesn't provide the test list.
 *
 * <p>A core driver can be associated with one lister class. The lister class name should be
 * <DRIVER_NAME> + "Lister". There is no lister for driver decorators.
 *
 * <p>Note this kind of test lister is running on user(client) machines only. They are NOT run on
 * devices.
 */
public interface Lister {

  /**
   * Generates the tests of the given job. If can not generate any test from the job, will throw out
   * exception.
   *
   * @return a list of generated test names, will never be null or empty
   * @throws MobileHarnessException if fails to generate the test list
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  List<String> listTests(JobInfo jobInfo) throws MobileHarnessException, InterruptedException;
}
