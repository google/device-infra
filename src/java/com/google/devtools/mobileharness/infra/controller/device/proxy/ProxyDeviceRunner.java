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

package com.google.devtools.mobileharness.infra.controller.device.proxy;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.proxy.DeviceProxy;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

class ProxyDeviceRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Factory for {@link ProxyDeviceRunner}. */
  interface Factory {

    ProxyDeviceRunner create(
        String formattedDeviceLocator,
        ProxyDeviceRequirement deviceRequirement,
        TestLocator testLocator,
        JobSetting jobSetting);
  }

  private final String formattedDeviceLocator;
  private final ProxyDeviceRequirement deviceRequirement;
  private final TestLocator testLocator;
  private final ReflectionUtil reflectionUtil;
  private final JobSetting jobSetting;

  private final Object releaseDeviceLock = new Object();

  @GuardedBy("releaseDeviceLock")
  private boolean deviceReleased;

  @GuardedBy("releaseDeviceLock")
  private DeviceProxy deviceProxy;

  @Inject
  ProxyDeviceRunner(
      @Assisted String formattedDeviceLocator,
      @Assisted ProxyDeviceRequirement deviceRequirement,
      @Assisted TestLocator testLocator,
      @Assisted JobSetting jobSetting,
      ReflectionUtil reflectionUtil) {
    this.formattedDeviceLocator = formattedDeviceLocator;
    this.deviceRequirement = deviceRequirement;
    this.testLocator = testLocator;
    this.reflectionUtil = reflectionUtil;
    this.jobSetting = jobSetting;
  }

  Device leaseDevice() throws MobileHarnessException, InterruptedException {
    // Creates DeviceProxy.
    DeviceProxy deviceProxy = createDeviceProxy();
    synchronized (releaseDeviceLock) {
      if (deviceReleased) {
        throw new InterruptedException(
            String.format("Skip leasing %s because it has been released", formattedDeviceLocator));
      }
      this.deviceProxy = deviceProxy;
    }

    // Leases Device.
    logger.atInfo().log("Leasing %s", formattedDeviceLocator);
    Device device = deviceProxy.leaseDevice();
    logger.atInfo().log("Leased %s", formattedDeviceLocator);
    return device;
  }

  void releaseDevice() {
    DeviceProxy deviceProxy;
    synchronized (releaseDeviceLock) {
      deviceReleased = true;
      deviceProxy = this.deviceProxy;
    }
    if (deviceProxy == null) {
      logger.atInfo().log(
          "Skip releasing %s because it has not been leased", formattedDeviceLocator);
    } else {
      // Releases Device.
      logger.atInfo().log("Releasing %s", formattedDeviceLocator);
      deviceProxy.releaseDevice();
      logger.atInfo().log("Released %s", formattedDeviceLocator);
    }
  }

  private DeviceProxy createDeviceProxy() throws MobileHarnessException {
    logger.atInfo().log("Creating device proxy for %s", formattedDeviceLocator);
    SubDeviceSpec subDeviceSpec =
        deviceRequirement.subDeviceSpecs().getSubDevice(deviceRequirement.subDeviceIndex());
    String deviceProxyClassName = getDeviceProxyClassName(subDeviceSpec.type());

    // Instantiates DeviceProxy.
    try {
      Class<? extends DeviceProxy> deviceProxyClass =
          reflectionUtil.loadClass(
              deviceProxyClassName, DeviceProxy.class, getClass().getClassLoader());
      return Guice.createInjector(new DeviceProxyModule(deviceRequirement, testLocator, jobSetting))
          .getInstance(deviceProxyClass);
    } catch (MobileHarnessException | CreationException | ProvisionException e) {
      throw new MobileHarnessException(
          InfraErrorId.DM_DEVICE_PROXY_INSTANTIATION_ERROR,
          String.format(
              "Failed to instantiate DeviceProxy [%s] for %s",
              deviceProxyClassName, formattedDeviceLocator),
          e);
    } catch (ClassNotFoundException e) {
      throw new MobileHarnessException(
          InfraErrorId.DM_DEVICE_PROXY_CLASS_NOT_FOUND,
          String.format(
              "DeviceProxy class [%s] for %s is not found in the jar. Add it as runtime_deps?",
              deviceProxyClassName, formattedDeviceLocator),
          e);
    }
  }

  private static String getDeviceProxyClassName(String deviceType) {
    return String.format(
        "com.google.devtools.mobileharness.api.devicemanager.proxy.%sProxy", deviceType);
  }

  private static class DeviceProxyModule extends AbstractModule {

    private final ProxyDeviceRequirement deviceRequirement;
    private final TestLocator testLocator;
    private final JobSetting jobSetting;

    private DeviceProxyModule(
        ProxyDeviceRequirement deviceRequirement, TestLocator testLocator, JobSetting jobSetting) {
      this.deviceRequirement = deviceRequirement;
      this.testLocator = testLocator;
      this.jobSetting = jobSetting;
    }

    @Override
    protected void configure() {
      bind(ProxyDeviceRequirement.class).toInstance(deviceRequirement);
      bind(TestLocator.class).toInstance(testLocator);
      bind(JobSetting.class).toInstance(jobSetting);
    }
  }
}
