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

package com.google.devtools.mobileharness.infra.client.api;

import com.google.common.base.Ascii;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.Annotations.EnvThreadPool;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ExtraGlobalInternalPlugins;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ExtraJobInternalPlugins;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.Annotations.JobThreadPool;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ShutdownJobThreadWhenShutdownProcess;
import com.google.devtools.mobileharness.infra.client.api.controller.job.JobManagerCore;
import com.google.devtools.mobileharness.infra.client.api.controller.job.JobManagerCoreFactory;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecModeUtil;
import com.google.devtools.mobileharness.infra.client.api.plugin.JobReporter;
import com.google.devtools.mobileharness.infra.client.api.util.lister.TestLister;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Device Infra client API for running tests. */
public class ClientApi {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Job manager which manages all the running jobs. */
  private final JobManagerCore jobManager;

  private final Supplier<String> clientHostnameSupplier;

  /**
   * EventBus for all {@link
   * com.google.wireless.qa.mobileharness.shared.controller.event.ControllerEvent}s.
   */
  private final Future<?> jobManagerThreadFuture;

  /** Creates Device Infra client API for running tests with Device Infra. */
  @Inject
  public ClientApi(
      @EnvThreadPool ListeningExecutorService envThreadPool,
      @JobThreadPool ListeningExecutorService jobThreadPool,
      JobManagerCoreFactory jobManagerCoreFactory,
      @ExtraGlobalInternalPlugins ImmutableList<Object> extraGlobalInternalPlugins,
      @ExtraJobInternalPlugins ImmutableList<Object> extraJobInternalPlugins,
      @ShutdownJobThreadWhenShutdownProcess boolean shutdownJobThreadWhenShutdownProcess,
      @GlobalInternalEventBus EventBus globalInternalEventBus,
      NetworkUtil networkUtil) {
    jobManager =
        jobManagerCoreFactory.create(
            jobThreadPool, globalInternalEventBus, extraJobInternalPlugins);

    ImmutableList<Object> builtinGlobalInternalPlugins =
        ImmutableList.of(new JobReporter(), new TestLister(), jobManager);
    Stream.concat(builtinGlobalInternalPlugins.stream(), extraGlobalInternalPlugins.stream())
        .forEach(globalInternalEventBus::register);

    jobManagerThreadFuture = envThreadPool.submit(jobManager);

    if (shutdownJobThreadWhenShutdownProcess) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    jobThreadPool.shutdownNow();
                    envThreadPool.shutdownNow();
                  }));
    }

    clientHostnameSupplier =
        Suppliers.memoize(
            () -> {
              try {
                // This may take seconds.
                return networkUtil.getLocalHostName();
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log("Failed to get local hostname");
                return "unknown";
              }
            });
  }

  /**
   * Starts a job and return immediately. This is a non-blocking operation. Won't wait for the
   * execution of the job.
   */
  public void startJob(JobInfo jobInfo, ExecMode execMode)
      throws MobileHarnessException, InterruptedException {
    startJob(jobInfo, execMode, null);
  }

  /**
   * Starts a job and return immediately. This is a non-blocking operation. Won't wait for the
   * execution of the job.
   *
   * @param jobSpecificEventHandlers handlers to receive job/test events of this specific job
   */
  public void startJob(
      JobInfo jobInfo, ExecMode execMode, @Nullable Collection<Object> jobSpecificEventHandlers)
      throws MobileHarnessException, InterruptedException {
    addCommonJobProperties(jobInfo, execMode);
    try {
      // Prepares job plugins.
      List<Object> jobPlugins = new ArrayList<>();
      if (jobSpecificEventHandlers != null) {
        jobPlugins.addAll(jobSpecificEventHandlers);
      }

      // Finally, starts the job.
      jobManager.startJob(jobInfo, execMode, jobPlugins);
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      jobInfo.errors().add(e);
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_API_START_JOB_ERROR, "Failed to start job", e);
    } catch (Throwable e) {
      jobInfo.errors().add(ErrorCode.JOB_CONFIG_ERROR, e);
      throw e;
    }
  }

  /** Kills a job if it is running. */
  public void killJob(String jobId) {
    jobManager.killJob(jobId);
  }

  /**
   * Close this client API and release allocated resources.
   *
   * <p>Should only be used by Gateway.
   */
  @SuppressWarnings("Interruption")
  public void close() {
    jobManagerThreadFuture.cancel(true);
  }

  /**
   * Waits until the job is finished or timeout.
   *
   * @return whether the job is done
   */
  @CanIgnoreReturnValue
  public boolean waitForJob(String jobId) {
    return jobManager.waitForJob(jobId);
  }

  /** Checks whether the job is finished or not. */
  public boolean isJobDone(String jobId) {
    return jobManager.isJobDone(jobId);
  }

  /** Gets the test message poster by the test id. */
  public Optional<TestMessagePoster> getTestMessagePoster(String testId) {
    return jobManager.getTestMessagePoster(testId);
  }

  private void addCommonJobProperties(JobInfo jobInfo, ExecMode execMode) {
    jobInfo.properties().add(Job.EXEC_MODE, Ascii.toLowerCase(ExecModeUtil.getModeName(execMode)));
    jobInfo.properties().add(Job.CLIENT_HOSTNAME, clientHostnameSupplier.get());
    jobInfo.properties().add(Job.CLIENT_VERSION, Version.CLIENT_VERSION.toString());
  }
}
