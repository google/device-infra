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

package com.google.devtools.deviceaction.framework.actions;

import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.annotations.GuiceAnnotations.FileResolver;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.common.utils.Resolver;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.deviceaction.framework.operations.ModuleCleaner;
import com.google.devtools.deviceaction.framework.operations.ModuleInstaller;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A utility class for {@code Action}. */
public class Actions {

  private final Devices devices;
  private final AaptUtil aaptUtil;
  private final ResourceHelper resourceHelper;
  private final LocalFileUtil localFileUtil;
  private final Resolver resolver;
  private final Sleeper sleeper;

  @Inject
  @SuppressWarnings("UnnecessarilyVisible")
  @Singleton
  public Actions(
      Devices devices,
      AaptUtil aaptUtil,
      ResourceHelper resourceHelper,
      LocalFileUtil localFileUtil,
      @FileResolver Resolver resolver,
      Sleeper sleeper) {
    this.devices = devices;
    this.aaptUtil = aaptUtil;
    this.resourceHelper = resourceHelper;
    this.localFileUtil = localFileUtil;
    this.resolver = resolver;
    this.sleeper = sleeper;
  }

  /** Creates an {@code Action} from a config. */
  public Action create(ActionConfig actionConfig)
      throws DeviceActionException, InterruptedException {
    switch (actionConfig.cmd()) {
      case INSTALL_MAINLINE:
        return createInstallMainline(actionConfig);
      default:
        throw new DeviceActionException(
            "UNKNOWN_COMMAND",
            ErrorType.CUSTOMER_ISSUE,
            "The command " + actionConfig.cmd() + " is not recognized");
    }
  }

  private InstallMainline createInstallMainline(ActionConfig actionConfig)
      throws DeviceActionException, InterruptedException {
    AndroidPhone androidPhone =
        devices.createAndroidPhone(
            actionConfig.actionSpec().getUnary().getFirst().getUuid(),
            actionConfig.firstSpec().get().getAndroidPhoneSpec());
    InstallMainlineSpec spec =
        actionConfig.actionSpec().getUnary().getExtension(InstallMainlineSpec.ext);
    ImmutableMultimap<String, File> resolvedFiles = resolver.resolve(spec.getFilesList());
    return new InstallMainline(
        new ModuleCleaner(androidPhone),
        new ModuleInstaller(androidPhone, sleeper),
        new ModulePusher(androidPhone, localFileUtil, resourceHelper),
        spec,
        androidPhone,
        aaptUtil,
        localFileUtil,
        resolvedFiles);
  }
}
