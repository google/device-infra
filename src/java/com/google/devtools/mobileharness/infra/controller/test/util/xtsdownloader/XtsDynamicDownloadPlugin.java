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

package com.google.devtools.mobileharness.infra.controller.test.util.xtsdownloader;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** An interface for xts dynamic downloader built in plugin. */
public interface XtsDynamicDownloadPlugin {
  /** Parses the device and test info to generate dynamic download file info */
  XtsDynamicDownloadInfo parse(TestInfo test, String deviceId)
      throws MobileHarnessException, InterruptedException;

  /** Downloads the xts files based on the download url link */
  void downloadXtsFiles(
      XtsDynamicDownloadInfo xtsDynamicDownloadInfo, TestInfo test, String deviceId)
      throws MobileHarnessException, InterruptedException;
}
