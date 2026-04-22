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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for taking a screenshot of a device. */
@Singleton
public final class TakeScreenshotHandler {

  private final ScreenshotActionHelper screenshotActionHelper;

  @Inject
  TakeScreenshotHandler(ScreenshotActionHelper screenshotActionHelper) {
    this.screenshotActionHelper = screenshotActionHelper;
  }

  public ListenableFuture<TakeScreenshotResponse> takeScreenshot(
      TakeScreenshotRequest request, UniverseScope universe) {
    return screenshotActionHelper.takeScreenshot(request, universe);
  }
}
