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

package com.google.devtools.atsconsole.controller.sessionplugin;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.atsconsole.controller.proto.DeviceDescriptorProto.DeviceDescriptor;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.inject.Guice;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ListDevicesCommandHandlerTest {

  @Inject private ListDevicesCommandHandler listDevicesCommandHandler;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
    listDevicesCommandHandler = spy(listDevicesCommandHandler);
  }

  @Test
  public void handle() throws Exception {
    when(listDevicesCommandHandler.listDevices())
        .thenReturn(
            ImmutableList.of(
                DeviceDescriptor.newBuilder()
                    .setSerial("abc")
                    .setDeviceState("ONLINE")
                    .setAllocationState("Available")
                    .setProduct("bullhead")
                    .setProductVariant("bullhead")
                    .setBuildId("MTC20K")
                    .setBatteryLevel("100")
                    .build()));

    assertThat(listDevicesCommandHandler.handle(ListDevicesCommand.getDefaultInstance()))
        .isEqualTo(
            AtsSessionPluginOutput.newBuilder()
                .setSuccess(
                    Success.newBuilder()
                        .setOutputMessage(
                            "Serial\tState\tAllocation\tProduct\tVariant\tBuild\tBattery\n"
                                + "abc\tONLINE\tAvailable\tbullhead\tbullhead\tMTC20K\t100"))
                .build());
  }
}
