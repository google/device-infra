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
import com.google.devtools.deviceaction.common.utils.CommandHistoryWriter;
import com.google.devtools.deviceaction.common.utils.Resolver;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.deviceaction.framework.operations.ImageZipFlasher;
import com.google.devtools.deviceaction.framework.operations.ModuleCleaner;
import com.google.devtools.deviceaction.framework.operations.ModuleInstaller;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.operations.OtaSideloader;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecorder;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.File;

/** A utility class for {@code Action}. */
public class Actions {

  private final Devices devices;
  private final AaptUtil aaptUtil;
  private final ResourceHelper resourceHelper;
  private final LocalFileUtil localFileUtil;
  private final Resolver resolver;
  private final Sleeper sleeper;
  private final CommandHistoryWriter writer;

  private final QuotaManager quotaManager;

  public Actions(
      Devices devices,
      AaptUtil aaptUtil,
      ResourceHelper resourceHelper,
      QuotaManager quotaManager,
      LocalFileUtil localFileUtil,
      @FileResolver Resolver resolver,
      CommandHistoryWriter writer,
      Sleeper sleeper) {
    this.devices = devices;
    this.aaptUtil = aaptUtil;
    this.resourceHelper = resourceHelper;
    this.quotaManager = quotaManager;
    this.localFileUtil = localFileUtil;
    this.resolver = resolver;
    this.sleeper = sleeper;
    this.writer = writer;
  }

  /** Creates an {@code Action} from a config. */
  public Action create(ActionConfig actionConfig)
      throws DeviceActionException, InterruptedException {
    switch (actionConfig.cmd()) {
      case INSTALL_MAINLINE:
        return createInstallMainline(actionConfig);
      case RESET:
        return createReset(actionConfig);
      default:
        throw new DeviceActionException(
            "UNKNOWN_COMMAND",
            ErrorType.CUSTOMER_ISSUE,
            "The command " + actionConfig.cmd() + " is not recognized");
    }
  }

  /** Performs all tasks before an action. */
  public void preAction() throws DeviceActionException {
    writer.init();
    CommandRecorder.getInstance().addListener(writer);
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
    PackageUpdateTracker packageUpdateTracker =
        new PackageUpdateTracker(androidPhone, aaptUtil, localFileUtil);
    return new InstallMainline(
        packageUpdateTracker,
        new ModuleCleaner(androidPhone),
        new ModuleInstaller(androidPhone, sleeper),
        new ModulePusher(androidPhone, localFileUtil, resourceHelper),
        spec,
        androidPhone,
        resolvedFiles,
        sleeper);
  }

  private Reset createReset(ActionConfig actionConfig)
      throws DeviceActionException, InterruptedException {
    AndroidPhone androidPhone =
        devices.createAndroidPhone(
            actionConfig.actionSpec().getUnary().getFirst().getUuid(),
            actionConfig.firstSpec().get().getAndroidPhoneSpec());
    PackageUpdateTracker packageUpdateTracker =
        new PackageUpdateTracker(androidPhone, aaptUtil, localFileUtil);
    ResetSpec spec = actionConfig.actionSpec().getUnary().getExtension(ResetSpec.ext);
    ImmutableMultimap<String, File> resolvedFiles = resolver.resolve(spec.getFilesList());
    return new Reset(
        packageUpdateTracker,
        new ModulePusher(androidPhone, localFileUtil, resourceHelper),
        new OtaSideloader(androidPhone, quotaManager),
        new ImageZipFlasher(
            androidPhone, localFileUtil, resourceHelper, quotaManager, new CommandExecutor()),
        spec,
        androidPhone,
        resolvedFiles);
  }
}
