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
import static org.mockito.Mockito.verify;

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
  @SuppressWarnings("CheckReturnValue")
  public void finish_writesUnparsedLinesToFile() {
    String unparsableLine1 = "unparsable line 1";
    String unparsableLine2 = "unparsable line 2";
    var unused = logcatLineProxy.onLine(unparsableLine1);
    logcatLineProxy.onLine("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message");
    unused = logcatLineProxy.onLine(unparsableLine2);

    assertThat(logcatLineProxy.getUnparsedLines())
        .containsExactly(unparsableLine1, unparsableLine2);
  }
}
