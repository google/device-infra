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

package com.google.devtools.mobileharness.platform.android.logcat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.ANDROID_RUNTIME;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.NATIVE;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.FAILURE;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.IGNORED;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LogcatLineProxyTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LineProcessor mockLineProcessor1;
  @Mock private LineProcessor mockLineProcessor2;

  private LogcatLineProxy logcatLineProxy;

  @Before
  public void setUp() throws Exception {
    logcatLineProxy = new LogcatLineProxy();
  }

  @Test
  public void onLine_parsableLine_invokesProcessors() {
    logcatLineProxy.addLineProcessor(mockLineProcessor1);
    logcatLineProxy.addLineProcessor(mockLineProcessor2);
    String line = "10-13 12:54:02.123  1234  5678 I ActivityManager: Some message";
    LogcatLine logcatLine = LogcatParser.parse(line).get();

    var unused = logcatLineProxy.onLine(line);

    verify(mockLineProcessor1).process(logcatLine);
    verify(mockLineProcessor2).process(logcatLine);
  }

  @Test
  public void onLine_unparsableLine_doesNotInvokeProcessors() {
    logcatLineProxy.addLineProcessor(mockLineProcessor1);
    String unparsableLine = "this is not a logcat line";

    var unused = logcatLineProxy.onLine(unparsableLine);

    assertThat(logcatLineProxy.getUnparsedLines()).containsExactly(unparsableLine);
  }

  @Test
  public void getLogcatEventsFromProcessors_getsLogcatEvents() {
    var crashEvent1 =
        new CrashEvent(
            new CrashedProcess("crashprocess1", 100, FAILURE, ANDROID_RUNTIME), "crash log line1");
    var crashEvent2 =
        new CrashEvent(
            new CrashedProcess("crashprocess2", 200, IGNORED, NATIVE), "crash log line2");
    when(mockLineProcessor1.getEvents()).thenReturn(ImmutableList.of(crashEvent1));
    when(mockLineProcessor2.getEvents()).thenReturn(ImmutableList.of(crashEvent2));

    logcatLineProxy.addLineProcessor(mockLineProcessor1);
    logcatLineProxy.addLineProcessor(mockLineProcessor2);

    assertThat(logcatLineProxy.getLogcatEventsFromProcessors())
        .containsExactly(crashEvent1, crashEvent2);
  }

  @Test
  public void finish_writesUnparsedLinesToFile() {
    String unparsableLine1 = "unparsable line 1";
    String unparsableLine2 = "unparsable line 2";
    var unused = logcatLineProxy.onLine(unparsableLine1);
    unused =
        logcatLineProxy.onLine("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message");
    unused = logcatLineProxy.onLine(unparsableLine2);

    assertThat(logcatLineProxy.getUnparsedLines())
        .containsExactly(unparsableLine1, unparsableLine2);
  }
}
