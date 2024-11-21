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

package com.google.wireless.qa.mobileharness.shared.model.job;

import static org.mockito.Mockito.spy;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Utility for mocking {@link TestInfo}. */
public class TestInfoMocker {

  public static final String FAKE_TEST_ID = "fake-test-id";
  public static final String FAKE_TEST_NAME = "fake-test-name";

  /**
   * Creates a mocked {@link TestInfo}, which calls real methods. can be stubbed.
   *
   * <p>In detail, this method is equivalent to
   *
   * <pre>{@code
   * JobInfo jobInfo = spy(<a real JobInfo with default settings and a mocked LocalFileUtil>);
   * TestInfo testInfo = spy(jobInfo.tests().add(<default test settings>));
   * return testInfo;
   * }</pre>
   */
  public static TestInfo mockTestInfo() {
    JobInfo jobInfo = JobInfoMocker.mockJobInfo();
    try {
      return spy(jobInfo.tests().add(FAKE_TEST_ID, FAKE_TEST_NAME));
    } catch (MobileHarnessException e) {
      throw new AssertionError(e);
    }
  }

  private TestInfoMocker() {}
}
