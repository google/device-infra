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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidForegroundServiceMessengerSpec;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

/**
 * This driver facilitates communication between API clients and an foreground Android Service with
 * the pre-defined messaging protocol.
 */
@DriverAnnotation(
    help =
        "This driver facilitates communication between API clients and an foreground Android"
            + " Service with the pre-defined messaging protocol. It assumes the Android Service is"
            + " installed before the test starts (for example,via {@link"
            + " AndroidInstallAppsDecorator} for on-demand installation). The driver starts the"
            + " service and provides it with the port number for communication.")
@TestAnnotation(
    required = false,
    help = "This is a self-defined meaningful test name. It has nothing to do with the driver.")
public class AndroidForegroundServiceMessenger extends BaseDriver
    implements AndroidForegroundServiceMessengerSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final TestMessageUtil testMessageUtil;

  /** The adb tool used to execute the related adb commands. */
  private final Adb adb;

  /** The Android ADB utility class used to forward TCP port. */
  private final AndroidAdbUtil androidAdbUtil;

  /** The local file utility class used for file transfer case. */
  private final LocalFileUtil localFileUtil;

  /** The Android file utility class to push file to device. */
  private final AndroidFileUtil androidFileUtil;

  /** The clock used to get the current time. */
  private final Clock clock;

  /**
   * The data stream that read data from devices. It's expected to be created once in {@link #run}
   * method and used util it's closed.
   */
  private volatile DataInputStream fromDevice;

  /** The last time the driver received a ping from the client. */
  @VisibleForTesting volatile Instant lastPingTime = Instant.EPOCH;

  private final Timer timer;

  /** The client socket that connects to the device. */
  private volatile Socket clientSocket = null;

  /**
   * The data stream that forward data to device. It's expected to be created once in {@link #run}
   * method and used util it's closed.
   *
   * <p>Accessing this field should be locked on the object itself.
   */
  private volatile DataOutputStream toDevice;

  private final ExecutorService eventExecutor;

  @Inject
  AndroidForegroundServiceMessenger(Device device, TestInfo testInfo) {
    this(
        device,
        testInfo,
        new Adb(),
        new AndroidAdbUtil(),
        new LocalFileUtil(),
        new AndroidFileUtil(),
        Clock.systemUTC(),
        Executors.newSingleThreadExecutor(),
        new TestMessageUtil(),
        new Timer());
  }

  @VisibleForTesting
  AndroidForegroundServiceMessenger(
      Device device,
      TestInfo testInfo,
      Adb adb,
      AndroidAdbUtil androidAdbUtil,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      Clock clock,
      ExecutorService eventExecutor,
      TestMessageUtil testMessageUtil,
      Timer timer) {
    super(device, testInfo);
    this.adb = adb;
    this.androidAdbUtil = androidAdbUtil;
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.clock = clock;
    this.eventExecutor = eventExecutor;
    this.testMessageUtil = testMessageUtil;
    this.timer = timer;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    int port;
    try {
      port = PortProber.pickUnusedPort();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROD_FOREGROUND_SERVICE_MESSENGER_INIT_ERROR,
          "Failed to pick an unused port.",
          e);
    }

    testInfo.log().atInfo().alsoTo(logger).log("Starting the service at port[%d]...", port);
    try {
      // The service package and name in the format like "com.yourcompany.yourservice/.YourService"
      // which could be used to trigger the service to run by "adb shell am" command
      String servicePackageAndName =
          testInfo
              .jobInfo()
              .params()
              .get(AndroidForegroundServiceMessengerSpec.ANDROID_SERVICE_PACKAGE_AND_NAME);

      // Forward the port to the device, both device and host will listen on this port.
      androidAdbUtil.forwardTcpPort(getDevice().getDeviceId(), port, port);

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Sending command to start the [%s] ...", servicePackageAndName);
      var unused =
          adb.runShell(
              getDevice().getDeviceId(),
              "am start-foreground-service -n " + servicePackageAndName + " --ei port " + port);

      // Sleep 3 seconds for service to be ready. If this turns out to be flaky, we can add
      // retry on client socket to verify the readiness of the service or the file status checking.
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(3));

      testInfo.log().atInfo().alsoTo(logger).log("Creating client socket...");
      clientSocket = createSocket(testInfo, port);
      fromDevice = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
      toDevice = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
      testInfo.log().atInfo().alsoTo(logger).log("Client socket created and ready to be used!");

      // Send the ready message to the client.
      testMessageUtil.sendMessageToTest(testInfo, ImmutableMap.of("namespace", "service_ready"));

      lastPingTime = clock.instant();
      TimerTask timeoutTask =
          new TimerTask() {
            @Override
            public void run() {
              if (clock.instant().isAfter(lastPingTime.plusSeconds(100))) {
                testInfo
                    .log()
                    .atWarning()
                    .alsoTo(logger)
                    .log(
                        "No ping received from client for more than 100 seconds - closing the"
                            + " socket.");
                timer.cancel();
                closeSilently(testInfo, clientSocket);
              }
            }
          };
      timer.scheduleAtFixedRate(timeoutTask, 10000, 10000);
      // We are now in reading mode util socket is closed or timeout from client ping.
      try {
        while (!Thread.currentThread().isInterrupted()) {
          testInfo.log().atInfo().alsoTo(logger).log("Reading data...");
          int dataSize = fromDevice.readInt();
          byte[] data = new byte[dataSize];
          fromDevice.readFully(data);
          testInfo.log().atInfo().alsoTo(logger).log("Forwarding data of size [%s]", dataSize);
          testMessageUtil.sendMessageToTest(
              testInfo,
              ImmutableMap.of(
                  "namespace",
                  "service_message",
                  "message",
                  Base64.getEncoder().encodeToString(data)));
        }
      } catch (IOException e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Interrupted while reading - which could be expected as the service close the"
                    + " connection.");
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROD_FOREGROUND_SERVICE_MESSENGER_INIT_ERROR,
          "Failed to initialize the socket. ",
          e);
    } finally {
      eventExecutor.shutdown();
      timer.cancel();
      closeSilently(testInfo, clientSocket);
      androidAdbUtil.removeTcpPortForward(getDevice().getDeviceId(), port);
    }

    // This always passes as it's not really a test but a communication channel between device and
    // client/users.
    testInfo.resultWithCause().setPass();
  }

  @Subscribe
  public void onTestMessage(TestMessageEvent event)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = event.getTest();
    if (event.getMessage().get("namespace").equals("client_message")) {
      eventExecutor.execute(() -> handleClientMessage(testInfo, event));
    } else if (event.getMessage().get("namespace").equals("client_file_copy")) {
      eventExecutor.execute(
          () -> {
            try {
              handleClientFileCopy(testInfo, event);
            } catch (InterruptedException e) {
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Interrupted while handling file copy. Exiting the Driver.");
              closeSilently(event.getTest(), clientSocket);
            }
          });
    } else if (event.getMessage().get("namespace").equals("client_ping")) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Received ping from client - resetting the last ping time");
      lastPingTime = clock.instant();
    }
  }

  private void handleClientMessage(TestInfo testInfo, TestMessageEvent event) {
    byte[] data = Base64.getDecoder().decode(event.getMessage().get("message"));
    try {
      DataOutputStream deviceToWrite = toDevice;
      if (deviceToWrite != null) {
        synchronized (deviceToWrite) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Forwarding the message of size [%s]", data.length);
          deviceToWrite.writeInt(data.length);
          deviceToWrite.write(data);
          deviceToWrite.flush();
        }
      } else {
        // This is not supposed to happen if client is reacting to the ready message.
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Device is not ready for message forwarding.");
      }
    } catch (IOException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Error on sending message to device.");
    }
  }

  private void handleClientFileCopy(TestInfo testInfo, TestMessageEvent event)
      throws InterruptedException {
    String sourceTag = event.getMessage().get("source_tag");
    String targetDeviceDir = event.getMessage().get("target_file_path_on_device");
    String sessiondId = event.getMessage().get("session_id");
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Copying files associated with tag[%s] to device[%s]", sourceTag, targetDeviceDir);

    // Wait for the file to be completely transferred.
    Optional<String> inputFilePathIfPresent =
        getInputFilePathIfPresent(testInfo, sourceTag, Duration.ofSeconds(60));
    if (inputFilePathIfPresent.isEmpty()) {
      // Construct the error message to be used on both log and test message.
      String errorMessage =
          String.format("Waited for request file tag [%s] but couldn't get any file.", sourceTag);
      testInfo.log().atInfo().alsoTo(logger).log("%s", errorMessage);
      sendTestMessageToTestIgnoreException(
          testInfo,
          ImmutableMap.of(
              "namespace",
              "service_file_copy",
              "is_success",
              "false",
              "error_message",
              errorMessage,
              "session_id",
              sessiondId));
      return;
    }

    String inputFileLabServerPath = inputFilePathIfPresent.get();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Pushing the request file path [%s] to the device at location: [%s]",
            inputFileLabServerPath, targetDeviceDir);
    try {
      androidFileUtil.push(
          event.getDeviceInfo().getId(),
          Integer.parseInt(
              event.getDeviceInfo().getDimensionList().stream()
                  .filter(d -> d.getName().equals("sdk_version"))
                  .findFirst()
                  .get()
                  .getValue()),
          inputFileLabServerPath,
          targetDeviceDir);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Pushed the request file path [%s] to the device at location: [%s]",
              inputFileLabServerPath, targetDeviceDir);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Failed to push the request file path [%s] to the device at location: [%s]",
              inputFileLabServerPath, targetDeviceDir);

      sendTestMessageToTestIgnoreException(
          testInfo,
          ImmutableMap.of(
              "namespace",
              "service_file_copy",
              "is_success",
              "false",
              "error_message",
              e.getMessage(),
              "session_id",
              sessiondId));
    }

    sendTestMessageToTestIgnoreException(
        testInfo,
        ImmutableMap.of(
            "namespace", "service_file_copy", "is_success", "true", "session_id", sessiondId));
  }

  private void sendTestMessageToTestIgnoreException(
      TestInfo testInfo, Map<String, String> message) {
    try {
      testMessageUtil.sendMessageToTest(testInfo, message);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Error on sending message to test.");
    }
  }

  /**
   * Creates a new socket connected to the device on the given port. The reason we use a different
   * method is to allow unit testing to override this method.
   *
   * @param port The port number to connect to.
   * @return The new socket.
   */
  @VisibleForTesting
  Socket createSocket(TestInfo testInfo, int port) throws IOException {
    try {
      return new Socket(Inet4Address.getByName("127.0.0.1"), port);
    } catch (IOException e) {
      testInfo
          .log()
          .atInfo()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to create socket with IPv4. Trying IPv6...");
      return new Socket(Inet6Address.getByName("::1"), port);
    }
  }

  private void closeSilently(TestInfo testInfo, Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        testInfo.log().atInfo().alsoTo(logger).log("Failed to create client socket");
        // ignore
      }
    }
  }

  /**
   * Returns true if the file is stable, false otherwise.
   *
   * <p>A file is considered stable if it has been completely transferred to the lab until 5 seconds
   * ago.
   */
  private boolean isFileStable(File file) throws MobileHarnessException {
    String filePath = file.getPath();
    if (localFileUtil.getFileSize(filePath) > 0) {
      // If the file bytes have been transferred and there's no change in the last 5 seconds in the
      // size, the file is assumed to have been completely transferred.
      return Instant.ofEpochMilli(localFileUtil.checkFile(filePath).lastModified())
          .isBefore(clock.instant().minusSeconds(5));
    }
    return false;
  }

  /**
   * Returns the Optional.of(inputFilePath) if the input file in the {@code inputFileTag} has
   * completely transferred to the lab until {@code waitTimeOut}, Optional.empty() otherwise.
   */
  @VisibleForTesting
  Optional<String> getInputFilePathIfPresent(
      TestInfo testInfo, String inputFileTag, Duration waitTimeOut) throws InterruptedException {
    Optional<String> inputFilePath =
        testInfo.jobInfo().files().get(inputFileTag).stream().findFirst();
    boolean fileCompletelyReceived = false;
    Stopwatch waitForFile = Stopwatch.createStarted();
    while (!fileCompletelyReceived && waitForFile.elapsed().compareTo(waitTimeOut) < 0) {
      try {
        // Check if the file is present yet in the files tag on lab server.
        if (inputFilePath.isPresent()) {
          fileCompletelyReceived = isFileStable(localFileUtil.checkFile(inputFilePath.get()));
        }
      } catch (Exception e) {
        testInfo.log().atInfo().alsoTo(logger).log("File is not stable yet. Waiting...");
      }
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1));
    }
    waitForFile.stop();
    return fileCompletelyReceived ? inputFilePath : Optional.empty();
  }
}
