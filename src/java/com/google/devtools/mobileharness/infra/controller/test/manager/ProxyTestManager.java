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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.manager.Annotations.KillExecutor;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link TestManager} for {@link ProxyTestRunner}s. */
@Singleton
public class ProxyTestManager extends TestManager<ProxyTestRunner>
    implements LabDirectTestRunnerHolder {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration WAIT_CLOSE_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration WAIT_CLOSE_INTERVAL = Duration.ofSeconds(3);

  private final ListeningExecutorService killExecutor;
  private final Sleeper sleeper;
  private final InstantSource instantSource;

  @Inject
  ProxyTestManager(
      @KillExecutor ListeningExecutorService killExecutor,
      Sleeper sleeper,
      InstantSource instantSource) {
    this.killExecutor = killExecutor;
    this.sleeper = sleeper;
    this.instantSource = instantSource;
  }

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

  /**
   * Kills any test running on the given device, and waits for it to close.
   *
   * <p>If no test is found running on the specified device, or if the test is already closed, this
   * method returns {@link Futures#immediateVoidFuture()}.
   *
   * <p>If a running test is found, the method attempts to kill it and waits up to 1 minute for the
   * test to become closed. It polls {@code isClosed()} every 3 seconds, and sends a {@code kill()}
   * signal in each interval if the test is not yet closed, to handle cases where tests might ignore
   * the initial signal. This 1-minute timeout is internal to the method for waiting for test
   * closure, and is not related to any test execution timeout.
   *
   * @param deviceId the device ID to check for running tests
   * @return a {@link ListenableFuture} which completes when the test has been confirmed closed, no
   *     test was found, or the 1-minute wait for closure times out.
   */
  public ListenableFuture<Void> killTestByDeviceId(String deviceId) {
    Optional<ProxyToDirectTestRunner> runnerOptional =
        getTestRunners().stream()
            .filter(runner -> runner instanceof ProxyToDirectTestRunner)
            .map(runner -> (ProxyToDirectTestRunner) runner)
            .filter(
                runner ->
                    runner.getDevices().stream().anyMatch(d -> d.getDeviceId().equals(deviceId)))
            .findFirst();

    if (runnerOptional.isEmpty()) {
      return Futures.immediateVoidFuture();
    }

    ProxyToDirectTestRunner runner = runnerOptional.get();
    if (runner.isClosed()) {
      logger.atInfo().log(
          "Test %s using device %s is already closed, no need to kill.",
          runner.getTestExecutionUnit().locator().id(), deviceId);
      return Futures.immediateVoidFuture();
    }
    logger.atInfo().log(
        "Found test %s using device %s, killing it and waiting for it to close.",
        runner.getTestExecutionUnit().locator().id(), deviceId);

    // Return a future that polls for isClosed().
    return killExecutor.submit(
        Callables.threadRenaming(
            () -> {
              String testId = runner.getTestExecutionUnit().locator().id();
              try {
                Instant deadline = instantSource.instant().plus(WAIT_CLOSE_TIMEOUT);
                while (instantSource.instant().isBefore(deadline)) {
                  sleeper.sleep(WAIT_CLOSE_INTERVAL);
                  if (runner.isClosed()) {
                    break;
                  }
                  runner.kill(true);
                }
                if (runner.isClosed()) {
                  logger.atInfo().log("Test %s closed successfully after cancellation.", testId);
                } else {
                  logger.atWarning().log(
                      "Test %s did not close within %s.", testId, WAIT_CLOSE_TIMEOUT);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.atWarning().withCause(e).log(
                    "Interrupted while waiting for test %s to close.", testId);
              }
              return null; // For Callable<Void>
            },
            () ->
                String.format(
                    "%s-kill-%s-on-%s",
                    Thread.currentThread().getName(),
                    runner.getTestExecutionUnit().locator().id(),
                    deviceId)));
  }

  public ProxyToDirectTestRunner getProxyToDirectTestRunner(String testId)
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
