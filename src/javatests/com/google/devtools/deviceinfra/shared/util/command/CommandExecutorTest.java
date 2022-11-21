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

package com.google.devtools.deviceinfra.shared.util.command;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.deviceinfra.shared.util.command.LineCallback.answerLn;
import static com.google.devtools.deviceinfra.shared.util.command.LineCallback.does;
import static com.google.devtools.deviceinfra.shared.util.command.LineCallback.stopWhen;
import static com.google.devtools.deviceinfra.shared.util.command.Timeout.fixed;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;
import com.google.devtools.deviceinfra.shared.util.path.PathUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CommandExecutorTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TEST_DATA_ROOT_PATH =
      "com_google_deviceinfra/src/javatests/com/google/devtools/deviceinfra/";
  private static final String TEST_SH = getRunfilesLocation("shared/util/command/testdata/test.sh");
  private static final String UNKILLABLE_SH =
      getRunfilesLocation("shared/util/command/testdata/unkillable.sh");
  private static final String STDOUT_STDERR_PRINTER =
      getRunfilesLocation("shared/util/command/stdout_stderr_printer");

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private Runnable timeoutCallback;
  @Mock private Consumer<String> lineConsumer;
  @Mock private Consumer<CommandResult> exitCallback;
  @Mock private LineCallback lineCallback;

  private CommandExecutor executor;

  @Before
  public void setUp() {
    executor = new CommandExecutor().setDefaultRedirectStderr(false);
  }

  @Test
  public void run() throws CommandException, InterruptedException {
    assertThat(executor.run(Command.of("echo", "Hello"))).isEqualTo("Hello\n");
  }

  @Test
  public void run_input() throws CommandException, InterruptedException {
    assertThat(executor.run(Command.of("/bin/bash", "-c", "read A; echo $A").inputLn("Y")))
        .isEqualTo("Y\n");
  }

  @Test(timeout = 9_500L)
  public void run_startTimeout() {
    assertThrows(
        CommandTimeoutException.class,
        () ->
            executor.run(
                Command.of(TEST_SH, "3s", "10s")
                    .startTimeout(Duration.ofMillis(100L))
                    .onTimeout(timeoutCallback)));

    verify(timeoutCallback, timeout(200)).run();
  }

  @Test
  public void run_noStartTimeout() throws CommandException, InterruptedException {
    assertThat(
            executor.run(
                Command.of(TEST_SH, "0s", "4s")
                    .startTimeout(Duration.ofSeconds(2L))
                    .onTimeout(timeoutCallback)))
        .isEqualTo("Hello\nBye\n");

    verify(timeoutCallback, never()).run();
  }

  @Test
  public void run_successStartCondition() throws CommandException, InterruptedException {
    assertThat(
            executor.run(
                Command.of(TEST_SH, "0s", "4s")
                    .startTimeout(Duration.ofSeconds(2L))
                    .successStartCondition(line -> line.equals("Hello"))
                    .onTimeout(timeoutCallback)))
        .isEqualTo("Hello\nBye\n");

    verify(timeoutCallback, never()).run();
  }

  @Test
  public void run_successStartCondition_fail() {
    assertThrows(
        CommandTimeoutException.class,
        () ->
            executor.run(
                Command.of(TEST_SH, "0s", "4s")
                    .startTimeout(Duration.ofSeconds(2L))
                    .successStartCondition(line -> line.equals("Hello, UDCluster"))
                    .onTimeout(timeoutCallback)));

    verify(timeoutCallback, timeout(200)).run();
  }

  @Test
  public void run_successStartCondition_runtimeException()
      throws CommandException, InterruptedException {
    assertThat(
            executor.run(
                Command.of("/bin/bash", "-c", "echo Hello; echo Bye")
                    .successStartCondition(
                        line -> {
                          throw new IllegalArgumentException();
                        })
                    .startTimeout(Duration.ofMinutes(2L))
                    .onStdout(does(lineConsumer))))
        .isEqualTo("Hello\nBye\n");

    verify(lineConsumer).accept("Hello");
    verify(lineConsumer).accept("Bye");
  }

  @Test
  public void run_stdoutCallback_stopWhen() throws CommandException, InterruptedException {
    assertThat(executor.run(Command.of(TEST_SH, "0s", "5s").onStdout(stopWhen(line -> true))))
        .isEqualTo("Hello\n");
  }

  @Test
  public void run_stdoutCallback_exception_stop()
      throws CommandException, InterruptedException, LineCallbackException {
    doThrow(new LineCallbackException("Error", null, true, false))
        .when(lineCallback)
        .onLine(anyString());

    assertThat(
            executor.run(
                Command.of(TEST_SH, "0s", "5s").onStdout(lineCallback).successExitCodes(0, 143)))
        .isEqualTo("Hello\n");
  }

  @Test
  public void run_stdoutCallback_exception_stopReading()
      throws CommandException, InterruptedException, LineCallbackException {
    doThrow(new LineCallbackException("Error", null, false, true))
        .when(lineCallback)
        .onLine(anyString());

    assertThat(
            executor.run(
                Command.of("/bin/bash", "-c", "echo Hello; echo Bye").onStdout(lineCallback)))
        .isEqualTo("Hello\nBye\n");

    verify(lineCallback).onLine(anyString());
  }

  @Test
  public void run_stdoutCallback_answer() throws CommandException, InterruptedException {
    assertThat(
            executor
                .exec(
                    Command.of(
                            "/bin/bash",
                            "-c",
                            "echo 0; for i in {1..5}; do read A; echo ${A}; done; "
                                + "read A; echo ${A} >&2")
                        .onStdout(
                            answerLn(
                                line -> {
                                  int number = Integer.parseInt(line);
                                  logger.atInfo().log(
                                      "Read number %s, answer with %s+1", number, number);
                                  return Optional.of(Integer.toString(number + 1));
                                })))
                .stderr())
        .isEqualTo("6\n");
  }

  @Test
  public void run_stdoutCallback_runtimeException() throws CommandException, InterruptedException {
    doThrow(RuntimeException.class).doNothing().when(lineConsumer).accept(anyString());

    assertThat(
            executor.run(
                Command.of("/bin/bash", "-c", "echo Hello; echo Bye").onStdout(does(lineConsumer))))
        .isEqualTo("Hello\nBye\n");

    verify(lineConsumer).accept("Hello");
    verify(lineConsumer).accept("Bye");
  }

  @Test
  public void run_redirectStderr() throws CommandException, InterruptedException {
    String output =
        executor.run(
            Command.of(STDOUT_STDERR_PRINTER)
                .extraEnv("JAVA_TOOL_OPTIONS", "-Xlog:os+container=error")
                .redirectStderr(true));
    assertThat(output).hasLength(31 * 100 * 2);
    assertThat(output).doesNotContain("OE");
    assertThat(output).doesNotContain("EO");
  }

  @Test
  public void asyncRun() throws ExecutionException, InterruptedException {
    assertThat(executor.asyncRun(Command.of("echo", "Hello")).get()).isEqualTo("Hello\n");
  }

  @Test
  public void asyncRun_fail() {
    assertThat(
            ((CommandFailureException)
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                executor
                                    .asyncRun(
                                        Command.of(
                                            "ls", "file_not_exist_" + Clock.systemUTC().millis()))
                                    .get())
                        .getCause())
                .result()
                .stderr())
        .contains("No such file or directory");
  }

  @Test
  public void asyncRun_cancelInterrupted() throws InterruptedException, CommandException {
    String fileName = PathUtil.join(tmpFolder.getRoot().getAbsolutePath(), "test_file_name");

    ListenableFuture<String> resultFuture =
        executor.asyncRun(
            Command.of(
                "/bin/bash", "-c", "echo Hello > " + fileName + "; sleep 3s; rm " + fileName));

    Thread.sleep(2_000L);

    resultFuture.cancel(true);

    Thread.sleep(3_000L);

    assertThat(executor.run(Command.of("/bin/bash", "-c", "cat " + fileName))).isEqualTo("Hello\n");
  }

  @Test
  public void asyncRun_cancelUninterrupted() throws InterruptedException, CommandException {
    String fileName = PathUtil.join(tmpFolder.getRoot().getAbsolutePath(), "test_file_name");

    ListenableFuture<String> resultFuture =
        executor.asyncRun(
            Command.of(
                "/bin/bash", "-c", "echo Hello > " + fileName + "; sleep 3s; rm " + fileName));

    Thread.sleep(2_000L);

    resultFuture.cancel(false);

    Thread.sleep(3_000L);

    assertThat(
            executor
                .exec(Command.of("/bin/bash", "-c", "cat " + fileName).successExitCodes(1))
                .stderr())
        .contains("No such file or directory");
  }

  @Test
  public void exec_fail() {
    assertThat(
            assertThrows(
                    CommandFailureException.class,
                    () ->
                        executor.exec(
                            Command.of("ls", "file_not_exist_" + Clock.systemUTC().millis())))
                .result()
                .stderr())
        .contains("No such file or directory");
  }

  @Test(timeout = 4_000L)
  public void exec_timeout() {
    assertThat(
            assertThrows(
                    CommandTimeoutException.class,
                    () ->
                        executor
                            .setDefaultTimeout(fixed(Duration.ofMillis(100L)))
                            .exec(Command.of("sleep", "10")))
                .result()
                .exitCode())
        .isEqualTo(143);
  }

  @Test(timeout = 4_000L)
  public void exec_timeoutCallback() {
    assertThrows(
        CommandTimeoutException.class,
        () ->
            executor.exec(
                Command.of("sleep", "10s")
                    .timeout(Duration.ofMillis(100L))
                    .onTimeout(timeoutCallback)));

    verify(timeoutCallback, timeout(100)).run();
  }

  @Test(timeout = 3_950L)
  public void exec_timeoutCallback_block() {
    doAnswer(
            invocation -> {
              Thread.sleep(4_000L);
              return null;
            })
        .when(timeoutCallback)
        .run();

    assertThrows(
        CommandTimeoutException.class,
        () ->
            executor.exec(
                Command.of("sleep", "1s")
                    .timeout(Duration.ofMillis(200L))
                    .onTimeout(timeoutCallback)));
  }

  @Test
  public void exec_stdoutCallback_does() throws CommandException, InterruptedException {
    executor.exec(
        Command.of("/bin/bash", "-c", "echo line0; echo line1").onStdout(does(lineConsumer)));

    verify(lineConsumer).accept("line0");
    verify(lineConsumer).accept("line1");
  }

  @Test
  public void exec_stdoutCallback_block() throws CommandException, InterruptedException {
    doAnswer(
            invocation -> {
              Thread.sleep(4_000L);
              return null;
            })
        .when(lineConsumer)
        .accept(anyString());

    executor.exec(
        Command.of("/bin/bash", "-c", "echo line0; echo line1").onStdout(does(lineConsumer)));

    verify(lineConsumer).accept("line0");
    verify(lineConsumer).accept("line1");
  }

  @Test
  public void exec_stderrCallback_does() throws CommandException, InterruptedException {
    assertThat(
            executor
                .exec(
                    Command.of("/bin/bash", "-c", "echo line0 >&2; echo line1 >&2")
                        .onStderr(does(lineConsumer)))
                .stderr())
        .isEqualTo("line0\nline1\n");

    verify(lineConsumer).accept("line0");
    verify(lineConsumer).accept("line1");
  }

  @Test
  public void exec_stderrCallback_redirectStderr() throws CommandException, InterruptedException {
    assertThat(
            executor
                .exec(Command.of("echo", "Hello").redirectStderr(true).onStderr(does(lineConsumer)))
                .stdout())
        .isEqualTo("Hello\n");
  }

  @Test
  public void exec_stderrCallback_block() throws CommandException, InterruptedException {
    doAnswer(
            invocation -> {
              Thread.sleep(4_000L);
              return null;
            })
        .when(lineConsumer)
        .accept(anyString());

    executor.exec(
        Command.of("/bin/bash", "-c", "echo line0 >&2; echo line1 >&2")
            .onStderr(does(lineConsumer)));

    verify(lineConsumer).accept("line0");
    verify(lineConsumer).accept("line1");
  }

  @Test
  public void exec_exitCallback() throws CommandException, InterruptedException {
    CommandResult result = executor.exec(Command.of("echo", "Hello").onExit(exitCallback));

    verify(exitCallback, timeout(5_000)).accept(result);
  }

  @Test
  public void exec_exitCallback_fail() {
    CommandResult result =
        assertThrows(
                CommandFailureException.class,
                () ->
                    executor.exec(
                        Command.of("echo", "Hello").onExit(exitCallback).successExitCodes(1)))
            .result();

    verify(exitCallback, timeout(5_000)).accept(result);
  }

  @Test(timeout = 3950L)
  public void exec_exitCallback_block() throws CommandException, InterruptedException {
    doAnswer(
            invocation -> {
              Thread.sleep(4_000L);
              return null;
            })
        .when(exitCallback)
        .accept(any(CommandResult.class));

    executor.exec(Command.of("echo", "Hello").onExit(exitCallback));
  }

  @Test
  public void exec_interrupted() throws CommandException, InterruptedException {
    Thread currentThread = Thread.currentThread();
    new Thread(
            () -> {
              try {
                Thread.sleep(4_000L);
                currentThread.interrupt();
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
            })
        .start();
    String fileName = PathUtil.join(tmpFolder.getRoot().getAbsolutePath(), "test_file_name");

    assertThrows(
        InterruptedException.class,
        () ->
            executor.exec(
                Command.of(
                    "/bin/bash", "-c", "echo Hello > " + fileName + "; sleep 8s; rm " + fileName)));

    Thread.sleep(10_000L);

    assertThat(executor.run(Command.of("/bin/bash", "-c", "cat " + fileName))).isEqualTo("Hello\n");
  }

  @Test
  public void exec_failToStartProcess() {
    assertThrows(CommandStartException.class, () -> executor.exec(Command.of("")));
  }

  @Test(timeout = 4_000L)
  public void start_kill() throws CommandStartException {
    CommandProcess process = executor.start(Command.of("sleep", "30s"));
    process.kill();

    assertThat(
            assertThrows(
                    CommandFailureException.class, () -> process.await(Duration.ofSeconds(20L)))
                .result()
                .exitCode())
        .isEqualTo(143);
  }

  @Test
  public void start_killForcibly() throws CommandException, InterruptedException {
    assertThat(getUnkillableProcesses()).isEmpty();

    CommandProcess process = executor.start(Command.of(UNKILLABLE_SH, "30s"));

    Thread.sleep(2_000L);

    assertThat(getUnkillableProcesses()).hasSize(1);

    process.kill();

    Thread.sleep(3_000L);

    assertThat(getUnkillableProcesses()).hasSize(1);

    process.killForcibly();

    Thread.sleep(3_000L);

    assertThat(getUnkillableProcesses()).isEmpty();
  }

  @Test
  public void start_killWithSignal() throws CommandException, InterruptedException {
    assertThat(getUnkillableProcesses()).isEmpty();

    CommandProcess process = executor.start(Command.of(UNKILLABLE_SH, "30s"));

    Thread.sleep(2_000L);

    assertThat(getUnkillableProcesses()).hasSize(1);

    process.kill();

    Thread.sleep(1_000L);

    assertThat(getUnkillableProcesses()).hasSize(1);

    process.killWithSignal(9);

    Thread.sleep(2_000L);

    assertThat(getUnkillableProcesses()).isEmpty();
  }

  @Test
  public void start_isAlive_stdoutCallback() throws CommandException, InterruptedException {
    doAnswer(
            invocation -> {
              Thread.sleep(4_000L);
              return null;
            })
        .when(lineConsumer)
        .accept(anyString());

    CommandProcess process =
        executor.start(Command.of("echo", "Hello").onStdout(does(lineConsumer)));

    Thread.sleep(2_000L);
    assertThat(process.isAlive()).isTrue();
  }

  @Test
  public void start_stdinStream() throws CommandException, IOException, InterruptedException {
    CommandProcess process =
        executor.start(Command.of("/bin/bash", "-c", "read A; read B; echo $A $B"));
    Writer writer =
        new BufferedWriter(new OutputStreamWriter(process.stdinStream(), StandardCharsets.UTF_8));
    writer.write("hello\n");
    writer.write("goodbye\n");
    writer.flush();
    assertThat(process.await(Duration.ofSeconds(10L)).stdout()).isEqualTo("hello goodbye\n");
  }

  @Test
  public void start_stdinWriter() throws CommandException, IOException, InterruptedException {
    CommandProcess process =
        executor.start(Command.of("/bin/bash", "-c", "read A; read B; echo $A $B"));
    Writer writer = process.stdinWriter();
    writer.write("hello\n");
    writer.write("goodbye\n");
    writer.flush();
    assertThat(process.await(Duration.ofSeconds(10L)).stdout()).isEqualTo("hello goodbye\n");
  }

  @Test
  public void start_stdinWriterAfterExited()
      throws CommandStartException, InterruptedException, IOException {
    CommandProcess process = executor.start(Command.of("echo", "Hello"));
    Thread.sleep(100L);
    process.stdinWriter().write("Input");
  }

  @Test
  public void start_stdoutCallback_does() throws CommandException, InterruptedException {
    CommandProcess process =
        executor.start(
            Command.of("/bin/bash", "-c", "echo line0 >&2; echo line1 >&2")
                .redirectStderr(true)
                .onStdout(does(lineConsumer)));

    verify(lineConsumer, timeout(5000)).accept("line0");
    verify(lineConsumer, timeout(5000)).accept("line1");

    process.await();
  }

  @Test
  public void start_await_timeout()
      throws CommandStartException,
          InterruptedException,
          CommandFailureException,
          CommandTimeoutException {
    CommandProcess process = executor.start(Command.of("/bin/bash", "-c", "sleep 3s; echo Hello"));
    assertThat(
            assertThrows(CommandException.class, () -> process.await(Duration.ofMillis(100L)))
                .getErrorId())
        .isEqualTo(BasicErrorId.CMD_PROCESS_AWAIT_TIMEOUT);
    assertThat(process.await().stdout()).isEqualTo("Hello\n");
  }

  @Test
  public void start_exitCallback() throws CommandStartException {
    CommandProcess process = executor.start(Command.of("echo", "Hello").onExit(exitCallback));

    verify(exitCallback, timeout(5_000)).accept(any(CommandResult.class));

    process.kill();
  }

  @Test
  public void asyncExec() throws ExecutionException, InterruptedException {
    assertThat(executor.asyncExec(Command.of("echo", "Hello")).get().stdout()).isEqualTo("Hello\n");
  }

  @Test
  public void asyncExec_fail() {
    assertThat(
            ((CommandFailureException)
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                executor
                                    .asyncExec(
                                        Command.of(
                                            "ls", "file_not_exist_" + Clock.systemUTC().millis()))
                                    .get())
                        .getCause())
                .result()
                .stderr())
        .contains("No such file or directory");
  }

  @Test
  public void asyncExec_cancelInterrupted() throws InterruptedException, CommandException {
    String fileName = PathUtil.join(tmpFolder.getRoot().getAbsolutePath(), "test_file_name");

    ListenableFuture<CommandResult> resultFuture =
        executor.asyncExec(
            Command.of(
                "/bin/bash", "-c", "echo Hello > " + fileName + "; sleep 3s; rm " + fileName));

    Thread.sleep(2_000L);

    resultFuture.cancel(true);

    Thread.sleep(3_000L);

    assertThat(executor.run(Command.of("/bin/bash", "-c", "cat " + fileName))).isEqualTo("Hello\n");
  }

  @Test
  public void asyncExec_cancelUninterrupted() throws InterruptedException, CommandException {
    String fileName = PathUtil.join(tmpFolder.getRoot().getAbsolutePath(), "test_file_name");

    ListenableFuture<CommandResult> resultFuture =
        executor.asyncExec(
            Command.of(
                "/bin/bash", "-c", "echo Hello > " + fileName + "; sleep 3s; rm " + fileName));

    Thread.sleep(2_000L);

    resultFuture.cancel(false);

    Thread.sleep(3_000L);

    assertThat(
            executor
                .exec(Command.of("/bin/bash", "-c", "cat " + fileName).successExitCodes(1))
                .stderr())
        .contains("No such file or directory");
  }

  @Test
  public void setDefaultWorkDirectory() throws CommandException, InterruptedException {
    assertThat(
            executor.setDefaultWorkDirectory(tmpFolder.getRoot().toPath()).run(Command.of("pwd")))
        .isEqualTo(tmpFolder.getRoot().getAbsolutePath() + "\n");
  }

  @Test
  public void setEnvironment() throws CommandException, InterruptedException {
    assertThat(
            executor
                .setBaseEnvironment(ImmutableMap.of("k1", "v1", "k2", "v2"))
                .run(Command.of("printenv", "k1", "k2")))
        .isEqualTo("v1\nv2\n");

    assertThat(
            executor
                .setBaseEnvironment(ImmutableMap.of("k3", "v3", "k4", "v4"))
                .run(Command.of("printenv", "k1", "k2", "k3", "k4").successExitCodes(0, 1)))
        .isEqualTo("v3\nv4\n");

    assertThat(executor.updateBaseEnvironment("k4", "v5").run(Command.of("printenv", "k3", "k4")))
        .isEqualTo("v3\nv5\n");

    assertThat(executor.run(Command.of("printenv", "k3", "k4").extraEnv("k4", "v4")))
        .isEqualTo("v3\nv4\n");
  }

  private List<String> getUnkillableProcesses() throws CommandException, InterruptedException {
    return Splitter.onPattern("\r\n|\n|\r")
        .omitEmptyStrings()
        .splitToStream(
            executor.run(Command.of("/bin/bash", "-c", "ps -ef | grep " + UNKILLABLE_SH)))
        .filter(line -> !line.contains("grep " + UNKILLABLE_SH))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private static String getRunfilesLocation(String suffix) {
    try {
      return com.google.devtools.build.runfiles.Runfiles.create()
          .rlocation(TEST_DATA_ROOT_PATH + suffix);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
