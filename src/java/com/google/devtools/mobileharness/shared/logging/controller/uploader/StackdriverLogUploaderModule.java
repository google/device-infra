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

import com.google.devtools.mobileharness.shared.logging.annotation.Annotations.StackdriverSecretFileName;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/** Module for {@link StackdriverLogUploader}. */
public class StackdriverLogUploaderModule extends AbstractModule {
  private final String secretFileName;

  public StackdriverLogUploaderModule(String secretFileName) {
    this.secretFileName = secretFileName;
  }

  @Override
  protected void configure() {
    bind(String.class).annotatedWith(StackdriverSecretFileName.class).toInstance(secretFileName);
    bind(LogUploader.class).to(StackdriverLogUploader.class).in(Singleton.class);
  }
}
