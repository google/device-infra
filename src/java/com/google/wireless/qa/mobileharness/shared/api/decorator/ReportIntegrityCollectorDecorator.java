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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidRemoteProvisioningUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.AbstractLifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import javax.inject.Inject;

/** A decorator that collects data from devices to validate report integrity. */
@DecoratorAnnotation(help = "Collects data from devices to validate report integrity.")
public class ReportIntegrityCollectorDecorator extends AbstractLifecycleDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String VB_META_DIGEST = "ro.boot.vbmeta.digest";

  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil;

  @Inject
  ReportIntegrityCollectorDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidAdbUtil androidAdbUtil,
      AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil) {
    super(decoratedDriver, testInfo);
    this.androidAdbUtil = androidAdbUtil;
    this.androidRemoteProvisioningUtil = androidRemoteProvisioningUtil;
  }

  @Override
  protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {

    String deviceId = getDevice().getDeviceId();

    // Collect VBMeta data.
    String vbMetaDigest = androidAdbUtil.getProperty(deviceId, ImmutableList.of(VB_META_DIGEST));
    testInfo.properties().add("cts:build_vb_meta_digest", vbMetaDigest);

    // Collect csr values.
    try {
      Map<String, byte[]> instanceNameToCsrMap =
          androidRemoteProvisioningUtil.getInstanceNameToCsr(deviceId);
      for (Map.Entry<String, byte[]> instanceNameToCsr : instanceNameToCsrMap.entrySet()) {
        String instanceName = nullToEmpty(instanceNameToCsr.getKey());
        String csr = Base64.getEncoder().encodeToString(instanceNameToCsr.getValue());
        testInfo.properties().add("cts:csr_" + instanceName, csr);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.WARNING)
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to collect csr values from %s", deviceId);
    }
  }

  @Override
  protected void tearDown(TestInfo testInfo) throws InterruptedException {
    // No action needed for tearDown.
  }
}
