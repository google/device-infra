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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.TradefedInvocations;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TradefedInvocationsFetcherTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Path JSTACK_OUTPUT_FILE_PATH =
      Path.of(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/platform/"
                  + "android/xts/runtime/tradefed_jstack_output.txt"));

  @Bind @Mock private CommandExecutor commandExecutor;

  @Inject private TradefedInvocationsFetcher tradefedInvocationsFetcher;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void fetchTradefedInvocations() throws Exception {
    when(commandExecutor.run(Command.of("/fake_jstack", "12345")))
        .thenReturn(Files.readString(JSTACK_OUTPUT_FILE_PATH));

    TradefedInvocations invocations =
        tradefedInvocationsFetcher.fetchTradefedInvocations(Path.of("/fake_jstack"), 12345L);

    assertThat(invocations)
        .isEqualTo(
            TradefedInvocations.newBuilder()
                .addInvocation(TradefedInvocation.newBuilder().addDeviceId("1B231FDEE000VH"))
                .addInvocation(TradefedInvocation.newBuilder().addDeviceId("18311FDEE002Y4"))
                .build());
  }
}
