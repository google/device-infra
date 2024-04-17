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

package com.google.devtools.deviceinfra.shared.logging.controller.uploader;

import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.shared.logging.controller.uploader.stackdriver.stub.StackdriverStub;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogProject;
import com.google.logging.v2.LogEntry;
import com.google.logging.v2.WriteLogEntriesRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link StackdriverLogUploader}. */
@RunWith(JUnit4.class)
public class StackdriverLogUploaderTest {

  private static final LogProject LOG_PROJECT = LogProject.LAB_SERVER;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private StackdriverStub stackdriverStub;

  private StackdriverLogUploader stackdriverLogUploader;

  @Before
  public void setUp() {
    stackdriverLogUploader = new StackdriverLogUploader(stackdriverStub, LOG_PROJECT);
  }

  @Test
  public void uploadLogs() throws Exception {
    stackdriverLogUploader.uploadLogs(ImmutableList.of(LogEntry.getDefaultInstance()));

    verify(stackdriverStub)
        .writeLogEntries(
            WriteLogEntriesRequest.newBuilder()
                .addEntries(LogEntry.getDefaultInstance())
                .setLogName(LOG_PROJECT.getLogName())
                .build());
  }
}
