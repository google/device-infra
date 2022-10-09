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
import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandOutputSinkTest {

  @Test
  public void awaitResult() throws IOException, InterruptedException, TimeoutException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(true, true, 1);
    try (OutputStream outputStream = commandOutputSink.openStream()) {
      Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      writer.write("Hello");
      writer.flush();
    }
    assertThrows(
        TimeoutException.class, () -> commandOutputSink.awaitResult(Duration.ofMillis(10L)));
    assertThat(commandOutputSink.isClosed()).isFalse();
    commandOutputSink.closePipe();
    assertThat(commandOutputSink.isClosed()).isTrue();
    assertThat(commandOutputSink.awaitResult()).isEqualTo("Hello");
    assertThat(commandOutputSink.awaitResult(Duration.ofSeconds(1L))).isEqualTo("Hello");
    assertThat(commandOutputSink.awaitResult()).isSameInstanceAs(commandOutputSink.awaitResult());
  }

  @Test
  public void awaitResult_disabled() throws InterruptedException {
    assertThat(new CommandOutputSink(true, false, 0).awaitResult()).isEmpty();
  }

  @Test
  public void awaitResult_notNeedResult() throws IOException, InterruptedException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(false, true, 1);
    try (OutputStream outputStream = commandOutputSink.openStream()) {
      Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      writer.write("Hello");
      writer.flush();
    }
    commandOutputSink.closePipe();
    assertThat(commandOutputSink.awaitResult()).isEmpty();
  }

  @Test
  public void getBufferedReader() throws IOException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(true, true, 1);
    BufferedReader bufferedReader = commandOutputSink.getBufferedReader();
    OutputStream outputStream = commandOutputSink.openStream();
    Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write("Hello\n");
    writer.flush();
    assertThat(bufferedReader.readLine()).isEqualTo("Hello");
  }

  @Test
  public void getBufferedReader_disabled() throws IOException {
    assertThat(new CommandOutputSink(true, true, 0).getBufferedReader().readLine()).isNull();
  }

  @Test
  public void getBufferedReader_notNeedReader() throws IOException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(true, false, 1);
    BufferedReader bufferedReader = commandOutputSink.getBufferedReader();
    OutputStream outputStream = commandOutputSink.openStream();
    Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write("Hello\n");
    writer.flush();
    assertThat(bufferedReader.readLine()).isNull();
  }

  @Test
  public void getBufferedReader_partiallyClose() throws IOException, InterruptedException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(true, false, 2);
    OutputStream outputStream = commandOutputSink.openStream();
    Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write("Hello\n");
    writer.flush();
    outputStream.close();
    outputStream = commandOutputSink.openStream();
    writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write("Hello\n");
    writer.flush();
    outputStream.close();
    assertThat(commandOutputSink.awaitResult()).isEqualTo("Hello\nHello\n");
  }

  @Test
  public void closePipe() throws IOException, InterruptedException, ExecutionException {
    CommandOutputSink commandOutputSink = new CommandOutputSink(false, true, 1);
    OutputStream outputStream = commandOutputSink.openStream();
    CountDownLatch latch = new CountDownLatch(1);
    Future<?> future =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  for (int i = 0; i < 1000; i++) {
                    outputStream.write(1);
                  }
                  latch.countDown();
                  for (int i = 0; i < 30; i++) {
                    outputStream.write(1);
                  }
                  return null;
                });
    latch.await();
    assertThrows(TimeoutException.class, () -> future.get(200L, TimeUnit.MILLISECONDS));
    commandOutputSink.closePipe();
    future.get();
  }
}
