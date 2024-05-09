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

package com.google.devtools.mobileharness.shared.logging.controller.uploader;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.logging.controller.uploader.stackdriver.stub.StackdriverStub;
import com.google.devtools.mobileharness.shared.logging.parameter.LogProject;
import com.google.inject.Inject;
import com.google.logging.v2.LogEntry;
import com.google.logging.v2.WriteLogEntriesRequest;
import java.util.List;

/** The uploader which will send logs to Stackdriver. */
public class StackdriverLogUploader implements LogUploader {

  private final StackdriverStub stackdriverStub;
  private final LogProject logProject;

  @Inject
  public StackdriverLogUploader(StackdriverStub stackdriverStub, LogProject logProject) {
    this.stackdriverStub = stackdriverStub;
    this.logProject = logProject;
  }

  @Override
  public void init() throws MobileHarnessException {
    stackdriverStub.init();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void uploadLogs(List<LogEntry> logEntries) throws MobileHarnessException {
    WriteLogEntriesRequest writeLogEntriesRequest =
        WriteLogEntriesRequest.newBuilder()
            .addAllEntries(logEntries)
            .setLogName(logProject.getLogName())
            .build();
    stackdriverStub.writeLogEntries(writeLogEntriesRequest);
  }
}
