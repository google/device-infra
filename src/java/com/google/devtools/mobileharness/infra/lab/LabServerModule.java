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

package com.google.devtools.mobileharness.infra.lab;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector.Component;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorsAndDispatchers;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.provider.AllocatedDeviceProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.device.provider.LocalDeviceProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceLauncherProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.test.launcher.ThreadPoolLauncherProvisioningModule;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import java.util.List;
import java.util.Map;

/** Guice module for {@link LabServer}. */
@SuppressWarnings("AvoidObjectArrays")
public class LabServerModule extends AbstractModule {

  private final BaseLabServerModule baseLabServerModule;
  private final EventBus globalInternalBus;

  public LabServerModule(
      List<String> mainArgs,
      Map<String, String> systemEnvironment,
      Map<String, String> systemProperties,
      EventBus globalInternalBus) {
    this.baseLabServerModule =
        new BaseLabServerModule(mainArgs, systemEnvironment, systemProperties, globalInternalBus);
    this.globalInternalBus = globalInternalBus;
  }

  @Override
  protected void configure() {
    install(baseLabServerModule);

    if (Flags.enableDeviceTestDecoupling.getNonNull()) {
      install(new AllocatedDeviceProvisioningModule());
      install(new ThreadPoolLauncherProvisioningModule());
    } else {
      install(new LocalDeviceProvisioningModule());
      install(new LocalDeviceLauncherProvisioningModule());
    }
  }

  @Provides
  @Singleton
  LocalDeviceManager provideLocalDeviceManager(
      ExternalDeviceManager externalDeviceManager, ListeningExecutorService threadPool)
      throws InterruptedException {
    DetectorsAndDispatchers detectorsAndDispatchers =
        new DetectorDispatcherSelector(Component.LAB_SERVER).selectDetectorsAndDispatchers();
    if (detectorsAndDispatchers.supportedDetectors().isEmpty()) {
      new SystemUtil()
          .exit(
              ExitCode.Lab.NO_DETECTOR,
              String.format(
                  "Your lab server is not properly configured: %n%s%n",
                  "All detectors are not supported by the current system."));
    }

    LocalDeviceManager localDeviceManager =
        new LocalDeviceManager(
            detectorsAndDispatchers.supportedDetectors(),
            detectorsAndDispatchers.supportedDispatchers(),
            /* keepGoing= */ true,
            threadPool,
            globalInternalBus,
            externalDeviceManager);
    localDeviceManager.initialize();
    return localDeviceManager;
  }
}
