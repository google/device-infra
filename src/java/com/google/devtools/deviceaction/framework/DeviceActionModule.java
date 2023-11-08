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

package com.google.devtools.deviceaction.framework;

import com.google.devtools.deviceaction.common.annotations.GuiceAnnotations.FileResolver;
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.common.utils.CommandHistoryWriter;
import com.google.devtools.deviceaction.common.utils.Resolver;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.common.utils.ResourceModule;
import com.google.devtools.deviceaction.framework.actions.Actions;
import com.google.devtools.deviceaction.framework.deviceconfigs.DeviceConfigModule;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.inject.Singleton;

/** Module that configures Device Action. */
public final class DeviceActionModule extends AbstractModule {

  private final ResourceHelper resourceHelper;
  private final QuotaManager quotaManager;

  public DeviceActionModule(ResourceHelper resourceHelper, QuotaManager quotaManager) {
    this.resourceHelper = resourceHelper;
    this.quotaManager = quotaManager;
  }

  @Override
  protected void configure() {
    install(new ResourceModule(resourceHelper));
    install(new DeviceConfigModule());
    bind(ActionConfigurer.class).to(MergingDeviceConfigurer.class).in(Singleton.class);
  }

  @Provides
  static Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }

  @Provides
  @Singleton
  Devices provideDevices(
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      BundletoolUtil bundletoolUtil,
      Sleeper sleeper) {
    return new Devices(
        androidAdbUtil,
        androidFileUtil,
        androidPackageManagerUtil,
        androidSystemSettingUtil,
        androidSystemStateUtil,
        bundletoolUtil,
        sleeper);
  }

  @Provides
  @Singleton
  Actions provideActions(
      Devices devices,
      AaptUtil aaptUtil,
      LocalFileUtil localFileUtil,
      @FileResolver Resolver resolver,
      Sleeper sleeper,
      CommandHistoryWriter writer) {
    return new Actions(
        devices, aaptUtil, resourceHelper, quotaManager, localFileUtil, resolver, writer, sleeper);
  }
}
