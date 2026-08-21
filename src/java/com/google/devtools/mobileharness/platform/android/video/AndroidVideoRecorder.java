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

package com.google.devtools.mobileharness.platform.android.video;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Path;
import java.util.List;

/** Interface for video recorders. */
public interface AndroidVideoRecorder {

  /**
   * Starts video recording on the device for the given test.
   *
   * @param deviceId the ID of the device to record
   * @param testId the ID of the running test
   * @param genFileDir the host directory path where generated video/screenshot files should be
   *     stored
   * @param spec configuration specifications for the video decorator
   * @throws MobileHarnessException if failed to start the video recording
   * @throws InterruptedException if the current thread is interrupted
   */
  void start(String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException;

  /**
   * Stops video recording and returns the path list of generated video files or screenshots.
   *
   * @return the list of paths to the generated video files or image frames
   * @throws MobileHarnessException if failed to stop the recording or process generated files
   * @throws InterruptedException if the current thread is interrupted
   */
  List<Path> stop() throws MobileHarnessException, InterruptedException;
}
