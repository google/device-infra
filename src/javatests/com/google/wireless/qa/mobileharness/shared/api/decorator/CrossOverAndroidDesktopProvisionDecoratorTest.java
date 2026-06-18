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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CrossOverAndroidDesktopProvisionDecorator}. */
@RunWith(JUnit4.class)
public class CrossOverAndroidDesktopProvisionDecoratorTest {

  private Driver decoratedDriver;
  private TestInfo testInfo;
  private Device device;
  private Log testLogger;
  private Api atInfo;
  private CommandExecutor commandExecutor;

  private CrossOverAndroidDesktopProvisionDecorator decorator;

  @Rule public final SetFlags flags = new SetFlags();

  @Before
  public void setUp() {
    decoratedDriver = mock(Driver.class);
    testInfo = mock(TestInfo.class);
    device = mock(Device.class);
    testLogger = mock(Log.class);
    atInfo = mock(Api.class);
    commandExecutor = mock(CommandExecutor.class);

    when(decoratedDriver.getDevice()).thenReturn(device);
    when(testInfo.log()).thenReturn(testLogger);
    when(testLogger.atInfo()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);

    decorator =
        new CrossOverAndroidDesktopProvisionDecorator(decoratedDriver, testInfo, commandExecutor);
  }

  @Test
  public void prepare_useCommandLineFlasherFalse_logsMessage() throws Exception {
    flags.set("crossover_use_command_line_flasher", "false");

    decorator.prepare(testInfo);

    verify(atInfo)
        .log(
            "CrossOverAndroidDesktopProvisionDecorator is not ready yet and will be implemented as"
                + " part of b/487343637. It will use foil-provision CIPD from CTP.");
    verifyNoInteractions(commandExecutor);
  }

  @Test
  public void prepare_useCommandLineFlasherTrue_runsCommandLineFlash() throws Exception {
    flags.set("crossover_use_command_line_flasher", "true");

    decorator.prepare(testInfo);

    verify(atInfo).log("Running command line based flash...");
    verify(commandExecutor).run(Command.of("true"));
  }
}
