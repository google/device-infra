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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.devtools.mobileharness.api.model.lab.in.LocalDimensions;

/** For managing the lab local dimension. */
public class LabDimensionManager {
  public static LabDimensionManager getInstance() {
    return INSTANCE;
  }

  private static final LabDimensionManager INSTANCE = new LabDimensionManager();

  private final LocalDimensions supportedLocalDimensions;

  private final LocalDimensions requiredLocalDimensions;

  private LabDimensionManager() {
    this.supportedLocalDimensions = new LocalDimensions();
    this.requiredLocalDimensions = new LocalDimensions();
  }

  public LocalDimensions getSupportedLocalDimensions() {
    return supportedLocalDimensions;
  }

  public LocalDimensions getRequiredLocalDimensions() {
    return requiredLocalDimensions;
  }
}
