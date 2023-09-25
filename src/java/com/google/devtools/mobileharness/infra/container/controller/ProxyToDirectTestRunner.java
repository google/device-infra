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

package com.google.devtools.mobileharness.infra.container.controller;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineStatus;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobOrTestFileUnit;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * {@link ProxyTestRunner} which runs the test in the current process using a {@link
 * DirectTestRunner}.
 *
 * <p>It is for non-container remote mode Mobile Harness test at lab server.
 *
 * <p>It exposes a {@link TestRunnerLauncher} by {@link #getConnectorTestRunnerLauncher()}, which
 * can be passed to a <b>proxied</b> {@link DirectTestRunner}. After that, when {@linkplain
 * com.google.devtools.mobileharness.infra.controller.test.TestRunner#start() start()} of the
 * proxied test runner is invoked, this test runner will use the same thread which runs {@linkplain
 * com.google.devtools.mobileharness.infra.controller.test.AbstractTestRunner#execute() execute()}
 * of this test runner to execute the proxied test (invoking its {@linkplain
 * com.google.devtools.mobileharness.infra.controller.test.AbstractTestRunner#execute() execute()}).
 *
 * <p>Note that if a test runner is proxied, invoking its {@linkplain
 * com.google.devtools.mobileharness.infra.controller.test.TestRunner#kill(boolean) kill()} will not
 * kill the test anymore, please kill the {@link ProxyToDirectTestRunner} instead.
 *
 * <p>If this test runner is {@linkplain #closeTest() closed} before a test runner is proxied, its
 * {@link #execute()} method will return an unknown test result immediately. If it is closed after a
 * test runner is proxied, it will not stop the proxied test runner.
 */
public class ProxyToDirectTestRunner extends AbstractProxyTestRunner<ProxyToDirectTestRunner> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final TestEngineLocator testEngineLocator;

  private final ConnectorTestRunnerLauncher connectorTestRunnerLauncher =
      new ConnectorTestRunnerLauncher();
  private final LabFileNotifier proxiedTestLabFileNotifier;
  private final CountDownLatch launchProxiedTestOrCloseTestLatch = new CountDownLatch(1);

  private final Object launchProxiedTestLock = new Object();

  @GuardedBy("launchProxiedTestLock")
  private boolean hasProxiedTestLaunched;

  @GuardedBy("launchProxiedTestLock")
  private MobileHarnessException cachedFinalizeTestError;

  @GuardedBy("launchProxiedTestLock")
  private boolean hasCachedKill;

  @GuardedBy("launchProxiedTestLock")
  private boolean cachedKillTimeout;

  public ProxyToDirectTestRunner(
      TestRunnerLauncher<? super ProxyToDirectTestRunner> launcher,
      TestExecutionUnit testExecutionUnit,
      Allocation allocation,
      LabFileNotifier proxiedTestLabFileNotifier,
      TestEngineLocator testEngineLocator)
      throws TestRunnerLauncherConnectedException {
    super(launcher, testExecutionUnit, allocation);
    this.proxiedTestLabFileNotifier = proxiedTestLabFileNotifier;
    this.testEngineLocator = testEngineLocator;
  }

  @Override
  public void notifyJobOrTestFile(JobOrTestFileUnit fileUnit) {
    proxiedTestLabFileNotifier.notifyJobOrTestFile(fileUnit);
  }

  @Override
  public final boolean isContainerMode() {
    return false;
  }

  @Override
  public final boolean isSandboxMode() {
    return false;
  }

  @Override
  public Optional<TestEngineLocator> getTestEngineLocator() {
    return Optional.of(testEngineLocator);
  }

  @Override
  public TestEngineStatus getTestEngineStatus() {
    return TestEngineStatus.READY;
  }

  @Override
  public Optional<MobileHarnessException> getTestEngineError() {
    return Optional.empty();
  }

  @Override
  @CanIgnoreReturnValue
  public boolean waitUntilTestEngineReady(Duration timeout) {
    // Returns immediately because a proxied test engine (lab server) is always ready.
    return true;
  }

  @Override
  public void asyncStartTestEngine() {
    // Does nothing because a proxied test engine (lab server) has already been started.
  }

  @Override
  public Optional<Duration> getTestEngineSetupTime() {
    return Optional.of(Duration.ZERO);
  }

  @Override
  protected void preExecute() {
    // Does nothing.
  }

  @Override
  protected TestExecutionResult execute() throws InterruptedException {
    // Waits a proxied direct test runner connected and launched or waits until the test is closed.
    try {
      logger.atInfo().log(
          "Wait direct test [%s] connected and launched", getTestExecutionUnit().locator().id());
      launchProxiedTestOrCloseTestLatch.await();
    } catch (InterruptedException e) {
      finalizeTest(
          new MobileHarnessException(
              InfraErrorId.TR_TEST_INTERRUPTED_WHEN_WAITING_DIRECT_TEST,
              "Interrupted when waiting direct test connected and launched",
              e));
      throw e;
    }

    if (isClosed()) {
      finalizeTest(
          new MobileHarnessException(
              InfraErrorId.TR_TEST_CLOSED_BEFORE_KICKED_OFF, "Test is closed before kicked off"));
      return TestExecutionResult.create(TestResult.UNKNOWN, PostTestDeviceOp.NONE);
    } else {
      // Executes the proxied test.
      logger.atInfo().log(
          "Execute proxied direct test [%s]", getTestExecutionUnit().locator().id());

      return connectorTestRunnerLauncher.executeProxiedTest();
    }
  }

  @Override
  protected void postKill(boolean timeout, int killCount) {
    boolean hasProxiedTestLaunched;
    synchronized (launchProxiedTestLock) {
      hasProxiedTestLaunched = this.hasProxiedTestLaunched;
      if (!hasProxiedTestLaunched) {
        hasCachedKill = true;
        cachedKillTimeout = timeout;
      }
    }
    if (hasProxiedTestLaunched) {
      connectorTestRunnerLauncher.getProxiedTestRunnerNonNull().kill(timeout);
    }
  }

  @Override
  protected void finalizeTest(MobileHarnessException error) {
    logger.atInfo().log("Finalize test with error: %s %s", error.getMessage(), error.getErrorId());
    boolean hasProxiedTestLaunched;
    synchronized (launchProxiedTestLock) {
      hasProxiedTestLaunched = this.hasProxiedTestLaunched;
      if (!hasProxiedTestLaunched) {
        cachedFinalizeTestError = error;
      }
    }
    if (hasProxiedTestLaunched) {
      connectorTestRunnerLauncher.finalizeProxiedTest(error);
    }
  }

  @Override
  public void closeTest() {
    super.closeTest();
    launchProxiedTestOrCloseTestLatch.countDown();
    // If the proxied test has started, does not stop it.
  }

  @Override
  protected final ProxyToDirectTestRunner self() {
    return this;
  }

  public TestRunnerLauncher<DirectTestRunner> getConnectorTestRunnerLauncher() {
    return connectorTestRunnerLauncher;
  }

  /**
   * @return the lab file notifier built-in plugin which can be registered to the proxied direct
   *     test runner.
   */
  public LabFileNotifier getProxiedTestLabFileNotifier() {
    return proxiedTestLabFileNotifier;
  }

  public Optional<DirectTestRunner> getProxiedDirectTestRunner() {
    return connectorTestRunnerLauncher.getProxiedTestRunner();
  }

  /**
   * Test runner launcher for connecting the proxy test runner and the direct test runner, which
   * lets the direct test runner use the thread which executes the proxy test runner to execute
   * itself.
   */
  private class ConnectorTestRunnerLauncher extends TestRunnerLauncher<DirectTestRunner> {

    @Override
    protected void asyncLaunchTest() {
      checkState(
          getTestExecutionUnit()
              .locator()
              .id()
              .equals(getTestRunner().getTestExecutionUnit().locator().id()),
          "ProxyToDirectTestRunner [%s] connects to a different test [%s]",
          getTestExecutionUnit().locator().id(),
          getTestRunner().getTestExecutionUnit().locator().id());

      // Handles cached finalize-test error message and killing request if exists.
      MobileHarnessException cachedFinalizeTestError;
      boolean hasCachedKill;
      boolean cachedKillTimeout;
      synchronized (launchProxiedTestLock) {
        hasProxiedTestLaunched = true;
        cachedFinalizeTestError = ProxyToDirectTestRunner.this.cachedFinalizeTestError;
        ProxyToDirectTestRunner.this.cachedFinalizeTestError = null;
        hasCachedKill = ProxyToDirectTestRunner.this.hasCachedKill;
        cachedKillTimeout = ProxyToDirectTestRunner.this.cachedKillTimeout;
      }
      if (cachedFinalizeTestError != null) {
        finalizeTest(cachedFinalizeTestError);
      }
      if (hasCachedKill) {
        getTestRunner().kill(cachedKillTimeout);
      }

      // Notifies the main thread of the proxy test runner to execute the proxied test.
      logger.atInfo().log(
          "Asynchronously launch proxied direct test [%s]", getTestExecutionUnit().locator().id());
      launchProxiedTestOrCloseTestLatch.countDown();
    }

    @Override
    protected void killTest() {
      // Does nothing.
    }

    @Override
    protected boolean isTestRunning() {
      return ProxyToDirectTestRunner.this.isRunning();
    }

    private Optional<DirectTestRunner> getProxiedTestRunner() {
      return isConnected() ? Optional.of(getTestRunner()) : Optional.empty();
    }

    private DirectTestRunner getProxiedTestRunnerNonNull() {
      return getTestRunner();
    }

    private TestExecutionResult executeProxiedTest() throws InterruptedException {
      return executeTest();
    }

    private void finalizeProxiedTest(MobileHarnessException error) {
      finalizeTest(error);
    }
  }
}
