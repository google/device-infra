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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidVideoRecorderTest {

  @Test
  public void startWithMaxDuration_defaultImplementation_delegatesToStart() throws Exception {
    FakeRecorder recorder = new FakeRecorder();
    Path genFileDir = Path.of("/gen");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("device_id", "test_id", genFileDir, spec, Duration.ofMinutes(1));

    assertThat(recorder.startedArgs)
        .containsExactly("device_id", "test_id", genFileDir, spec)
        .inOrder();
  }

  /** A recorder that only implements the abstract methods of {@link AndroidVideoRecorder}. */
  private static final class FakeRecorder implements AndroidVideoRecorder {
    private ImmutableList<Object> startedArgs = ImmutableList.of();

    @Override
    public void start(
        String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec) {
      startedArgs = ImmutableList.of(deviceId, testId, genFileDir, spec);
    }

    @Override
    public ImmutableList<Path> stop() {
      return ImmutableList.of();
    }
  }
}
