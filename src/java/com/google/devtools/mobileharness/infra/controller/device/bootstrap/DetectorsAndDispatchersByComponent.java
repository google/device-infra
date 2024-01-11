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

package com.google.devtools.mobileharness.infra.controller.device.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;

/**
 * TODO: Merges this class into {@link AllDetectorsAndDispatchers} after dynamically loading
 * detector/dispatcher classes.
 */
abstract class DetectorsAndDispatchersByComponent {

  abstract ImmutableList<Detector> detectorCandidates();

  abstract void addDispatchers(DispatcherManager dispatcherManager);
}
