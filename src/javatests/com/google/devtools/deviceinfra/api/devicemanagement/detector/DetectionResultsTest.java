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

package com.google.devtools.deviceinfra.api.devicemanagement.detector;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.api.devicemanagement.detector.DetectionResult.DetectionType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DetectionResults}. */
@RunWith(JUnit4.class)
public class DetectionResultsTest {
  private static final String DEVICE_CONTROL_ID = "DEVICE_CONTROL_ID";
  private static final Object DETECTION_DETAIL = new Object();
  private static final DetectionResult ADB_DETECTION_RESULT =
      DetectionResult.of(DEVICE_CONTROL_ID, DetectionType.ADB, DETECTION_DETAIL);
  private static final DetectionResult FASTBOOT_DETECTION_RESULT =
      DetectionResult.of(DEVICE_CONTROL_ID, DetectionType.FASTBOOT, DETECTION_DETAIL);

  @Test
  public void add_success() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResults.add(ADB_DETECTION_RESULT);

    assertThat(detectionResults.getAll().values()).containsExactly(ADB_DETECTION_RESULT);
  }

  @Test
  public void add_list_success() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResults.add(ImmutableList.of(ADB_DETECTION_RESULT, FASTBOOT_DETECTION_RESULT));

    assertThat(detectionResults.getAll())
        .containsExactly(
            DEVICE_CONTROL_ID, ADB_DETECTION_RESULT, DEVICE_CONTROL_ID, FASTBOOT_DETECTION_RESULT);
  }

  @Test
  public void remove_success() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResults.add(ImmutableList.of(ADB_DETECTION_RESULT, FASTBOOT_DETECTION_RESULT));
    detectionResults.remove(DEVICE_CONTROL_ID);

    assertThat(detectionResults.getAll()).isEmpty();
  }

  @Test
  public void getResultByType_success() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResults.add(ImmutableList.of(ADB_DETECTION_RESULT, FASTBOOT_DETECTION_RESULT));

    assertThat(detectionResults.getByType(DetectionType.ADB)).containsExactly(ADB_DETECTION_RESULT);
  }

  @Test
  public void getResultByTypeAndDetail_success() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResults.add(ImmutableList.of(ADB_DETECTION_RESULT, FASTBOOT_DETECTION_RESULT));

    assertThat(detectionResults.getByTypeAndDetail(DetectionType.ADB, DETECTION_DETAIL))
        .containsExactly(ADB_DETECTION_RESULT);
  }
}
