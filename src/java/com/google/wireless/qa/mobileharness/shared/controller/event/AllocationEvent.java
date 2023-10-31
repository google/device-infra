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

package com.google.wireless.qa.mobileharness.shared.controller.event;

import com.google.devtools.mobileharness.api.model.allocation.Allocation;

/**
 * Event to notify an allocation of devices for a test.
 *
 * <p>Posting of this event should not be in an allocation lock.
 */
public class AllocationEvent implements ControllerEvent {
  private final Allocation allocation;

  public AllocationEvent(Allocation allocation) {
    this.allocation = allocation;
  }

  public Allocation getAllocation() {
    return allocation;
  }
}
