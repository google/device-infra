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

package com.google.devtools.mobileharness.api.testrunner.event.test;

/**
 * Event which signals that a local test has ended.
 *
 * <p>The event will only be posted in local mode or at lab side of remote mode.
 *
 * <p><b>NOTE</b>: Please <b>MOCK</b> this class in your unit tests rather than implementing it.
 *
 * @since NA (the event is not be posted in tests for now)
 */
public interface LocalTestEndedEvent extends TestEndedEvent, DeviceFeaturedEvent {}
