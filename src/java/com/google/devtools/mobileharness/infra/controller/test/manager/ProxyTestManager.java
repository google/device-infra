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

package com.google.devtools.mobileharness.infra.controller.test.manager;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Optional;

/** {@link TestManager} for {@link ProxyTestRunner}s. */
public class ProxyTestManager extends TestManager<ProxyTestRunner>
    implements LabDirectTestRunnerHolder {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void killAndRemoveTest(String testId) {
    closeTest(testId);
    super.killAndRemoveTest(testId);
  }

  public void closeTest(String testId) {
    Optional<ProxyTestRunner> testRunner = getTestRunner(testId);
    if (testRunner.isPresent()) {
      logger.atInfo().log("Close test [%s]", testId);
      testRunner.get().closeTest();
    } else {
      logger.atWarning().log("Try to close non-existing test [%s]", testId);
    }
  }

  public ProxyTestRunner getProxyTestRunner(String testId) throws MobileHarnessException {
    return getTestRunnerNonEmpty(testId);
  }

  /**
   * {@inheritDoc}
   *
   * @throws MobileHarnessException if no corresponding {@link ProxyTestRunner} is found or the test
   *     is container mode
   */
  @Override
  public TestRunnerLauncher<? super DirectTestRunner> createTestRunnerLauncher(String testId)
      throws MobileHarnessException {
    return getProxyToDirectTestRunner(testId).getConnectorTestRunnerLauncher();
  }

  @Override
  public LabFileNotifier createLabFileNotifier(String testId) throws MobileHarnessException {
    return getProxyToDirectTestRunner(testId).getProxiedTestLabFileNotifier();
  }

  @Override
  public Optional<List<DeviceFeature>> getDeviceFeatures(String testId)
      throws MobileHarnessException {
    return getProxiedDirectTestRunner(testId).getDeviceFeatures();
  }

  @Override
  public TestInfo getTestInfo(String testId) throws MobileHarnessException {
    return getProxiedDirectTestRunner(testId).getTestInfo();
  }

  @Override
  public Dirs getJobDirs(String jobId, String testId) throws MobileHarnessException {
    return getProxyToDirectTestRunner(testId).getTestExecutionUnit().job().dirs();
  }

  private ProxyToDirectTestRunner getProxyToDirectTestRunner(String testId)
      throws MobileHarnessException {
    ProxyTestRunner proxyTestRunner = getTestRunnerNonEmpty(testId);
    if (proxyTestRunner.isContainerMode()) {
      throw new MobileHarnessException(
          InfraErrorId.TM_TEST_IN_CONTAINER_MODE,
          String.format("Test [%s] is container mode", testId));
    }
    return (ProxyToDirectTestRunner) proxyTestRunner;
  }

  public DirectTestRunner getProxiedDirectTestRunner(String testId) throws MobileHarnessException {
    return getProxyToDirectTestRunner(testId)
        .getProxiedDirectTestRunner()
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.TM_TEST_NOT_KICKED_OFF,
                    String.format("Test [%s] has not been kicked off", testId)));
  }
}
