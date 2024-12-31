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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaInstant;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;
import static com.google.devtools.mobileharness.shared.util.truth.Correspondences.containsAll;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.messaging.MessageDestinationNotFoundException;
import com.google.devtools.mobileharness.api.messaging.MessageEvent;
import com.google.devtools.mobileharness.api.messaging.SubscribeMessage;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.ComponentMessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.GlobalMessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingResult;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingStart;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception.TypeCase;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSendDestination;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSubscriberInfo;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSenderFinder;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManager;
import com.google.devtools.mobileharness.shared.context.InvocationContext.ContextScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationInfo;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClientApiTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final SetFlagsOss flags = new SetFlagsOss();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs("", /* printFailedLogs= */ true);
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Bind @GlobalInternalEventBus private EventBus globalInternalEventBus;

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  private final ListeningScheduledExecutorService scheduledThreadPool =
      ThreadPools.createStandardScheduledThreadPool("testing-scheduled-thread-pool", 1);

  @Inject private ClientApi clientApi;

  private MessagingManager messagingManager;

  private final List<MessageReception> receivingMessageReceptions = new ArrayList<>();
  private final SettableFuture<ImmutableList<MessageReception>> messageReceptions =
      SettableFuture.create();

  @Before
  public void setUp() {
    flags.setAllFlags(
        ImmutableMap.of(
            "detect_adb_device",
            "false",
            "external_adb_initializer_template",
            "true",
            "no_op_device_num",
            "1"));

    globalInternalEventBus = new EventBus();

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);

    messagingManager =
        Guice.createInjector(
                new AbstractModule() {
                  @Provides
                  MessageSenderFinder provideMessageSenderFinder() {
                    return clientApi.getJobManager();
                  }
                },
                BoundFieldModule.of(this))
            .getInstance(MessagingManager.class);
  }

  @Test
  public void startJob() throws Exception {
    JobInfo jobInfo = createJobInfo(/* sleepTimeSec= */ 5);
    List<String> jobStartResults = new ArrayList<>();

    try (var ignored =
        new ContextScope(
            ImmutableMap.of(
                InvocationType.OLC_CLIENT, InvocationInfo.sameDisplayId("fake_client_id")))) {
      clientApi.startJob(
          jobInfo,
          new LocalMode(),
          ImmutableList.of(
              new Object() {

                @Subscribe
                @SuppressWarnings("unused")
                private void onJobStart(JobStartEvent event) {
                  jobStartResults.add("1");
                }
              },
              new Object() {

                @Subscribe
                @SuppressWarnings("unused")
                private void onJobStart(JobStartEvent event) {
                  jobStartResults.add("2");
                }
              },
              new Object() {

                @Subscribe
                @SuppressWarnings("unused")
                private void onJobStart(JobStartEvent event) {
                  jobStartResults.add("3");
                }
              }));

      assertThat(jobInfo.properties().get(Job.EXEC_MODE)).isNotNull();
      assertThat(jobInfo.properties().get(Job.CLIENT_HOSTNAME)).isNotNull();
      assertThat(jobInfo.properties().get(Job.CLIENT_VERSION)).isNotNull();

      clientApi.waitForJob(jobInfo.locator().getId());

      assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

      TestInfo testInfo = jobInfo.tests().getOnly();
      assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
      assertThat(testInfo.log().get(0)).contains("Sleep for 5 seconds");

      String logs = captureLogs.getLogs();

      assertThat(Splitter.on('\n').splitToList(logs))
          .comparingElementsUsing(containsAll())
          .contains(ImmutableList.of("Sleep for 5 seconds", "{olc_client_id=fake_client_id}"));

      assertWithMessage(
              "Log of a passed MH job should not contain exception stack traces, which will"
                  + " confuse users when they debug a failed one")
          .that(logs)
          .doesNotContain("\tat ");

      assertThat(jobStartResults).containsExactly("1", "2", "3").inOrder();
    }
  }

  @Test
  public void killJob() throws Exception {
    JobInfo jobInfo = createJobInfo(/* sleepTimeSec= */ 60);

    clientApi.startJob(jobInfo, new LocalMode(), ImmutableList.of(this));

    clientApi.waitForJob(jobInfo.locator().getId());
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(10L));

    TestInfo testInfo = jobInfo.tests().getOnly();
    assertThat(testInfo.log().get(0)).contains("Interrupted from sleep");
  }

  @Test
  public void sendMessage() throws Exception {
    JobInfo jobInfo = createJobInfo(/* sleepTimeSec= */ 60);

    clientApi.startJob(jobInfo, new LocalMode(), ImmutableList.of(this));
    clientApi.waitForJob(jobInfo.locator().getId());

    Timestamp expectedResult = toProtoTimestamp(Instant.ofEpochSecond(246L));
    MessageSubscriberInfo messageSubscriberInfo =
        MessageSubscriberInfo.newBuilder()
            .setClassName(getClass().getName())
            .setMethodName("onMessage")
            .build();
    assertThat(messageReceptions.get())
        .comparingExpectedFieldsOnly()
        .containsExactly(
            MessageReception.newBuilder()
                .setSubscriberReceivingStart(
                    MessageReceivingStart.newBuilder().setSubscriberInfo(messageSubscriberInfo))
                .build(),
            MessageReception.newBuilder()
                .setSubscriberReceivingEnd(
                    MessageReceivingEnd.newBuilder()
                        .setSubscriberInfo(messageSubscriberInfo)
                        .setSuccess(
                            MessageReceivingResult.newBuilder()
                                .setSubscriberReceivingResult(Any.pack(expectedResult))))
                .build(),
            MessageReception.newBuilder()
                .setComponentMessageReceivingEnd(ComponentMessageReceivingEnd.getDefaultInstance())
                .build(),
            MessageReception.newBuilder()
                .setGlobalMessageReceivingEnd(GlobalMessageReceivingEnd.getDefaultInstance())
                .build())
        .inOrder();
  }

  @Subscribe
  private void onTestStarting(TestStartingEvent event) throws MessageDestinationNotFoundException {
    messagingManager.sendMessage(
        MessageSend.newBuilder()
            .setMessage(Any.pack(toProtoTimestamp(Instant.ofEpochSecond(123L))))
            .setDestination(
                MessageSendDestination.newBuilder()
                    .setTest(
                        MessageSendDestination.Test.newBuilder()
                            .setRootTestId(event.getTest().locator().getId())))
            .build(),
        receptions -> {
          for (MessageReception reception : receptions.getReceptionsList()) {
            receivingMessageReceptions.add(reception);
            if (reception.getTypeCase() == TypeCase.GLOBAL_MESSAGE_RECEIVING_END) {
              messageReceptions.set(ImmutableList.copyOf(receivingMessageReceptions));
            }
          }
        });
  }

  @Subscribe
  private void onDriverStarting(LocalDriverStartingEvent event) {
    if (event.getDriverName().equals("NoOpDriver")) {
      logFailure(
          scheduledThreadPool.schedule(
              () -> {
                logger.atInfo().log("Kill job");
                clientApi.killJob(event.getTest().jobInfo().locator().getId());
              },
              Duration.ofSeconds(1L)),
          Level.WARNING,
          "Error when killing job");
    }
  }

  @SubscribeMessage
  private Timestamp onMessage(MessageEvent<Timestamp> event) {
    return toProtoTimestamp(toJavaInstant(event.getMessage()).plus(Duration.ofSeconds(123L)));
  }

  private static JobInfo createJobInfo(int sleepTimeSec) {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_name"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .build())
            .build();
    jobInfo.params().add("sleep_time_sec", Integer.toString(sleepTimeSec));
    return jobInfo;
  }
}
