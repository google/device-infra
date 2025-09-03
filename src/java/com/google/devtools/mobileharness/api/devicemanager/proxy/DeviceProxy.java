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

package com.google.devtools.mobileharness.api.devicemanager.proxy;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/**
 * Proxy for leasing a device from a (remote) device provider and preparing the device so that it
 * can be used as a local {@link Device} in a local test.
 *
 * <p><b>Implementation Specifications</b>
 *
 * <p>An implementation class should be in <b>this package</b>. The class name should adhere to the
 * format "<b>[device_type]Proxy</b>", where "[device_type]" represents the device type supported by
 * the proxied {@link Device} created by this proxy. For example, an implementation
 * "NoOpDeviceProxy" will be responsible for creating proxied {@link Device}s whose supported device
 * types contain "NoOpDevice".
 *
 * <p>An implementation class should be able to be injected by <b>Guice</b> (containing a no-arg
 * constructor or a constructor annotated by annotations like {@link javax.inject.Inject}). An
 * implementation class can also be annotated by {@link DeviceProxyModule} to specify extra Guice
 * modules for creating its dependencies.
 *
 * <p>The following type are bound during the injection of an implementation class:
 *
 * <ul>
 *   <li>{@linkplain
 *       com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceRequirement
 *       ProxyDeviceRequirement} to provide the device requirement of a sub device of a job
 *   <li>{@linkplain com.google.devtools.mobileharness.api.model.job.TestLocator TestLocator} to
 *       provide a test locator
 *   <li>{@linkplain com.google.wireless.qa.mobileharness.shared.model.job.JobSetting JobSetting} to
 *       provide the job setting of the test
 * </ul>
 */
public interface DeviceProxy {

  /**
   * Leases a device for a sub device in a test.
   *
   * <p>This method should be able to handle thread interruptions properly.
   *
   * <p>This method is called once per instance, before {@link #releaseDevice()}.
   *
   * @return a fully prepared {@link Device}
   */
  Device leaseDevice() throws MobileHarnessException, InterruptedException;

  /**
   * Releases the leased device if any.
   *
   * <p>This method is called once per instance, after {@link #leaseDevice()}.
   *
   * <p>Before calling this method, if {@link #leaseDevice()} has not returned, it will be
   * interrupted.
   */
  void releaseDevice();
}
