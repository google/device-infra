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
 * Event which signals that a driver/decorator is going to start. For single device driver, this
 * event will be posted at each driver/decorator. For adhoc testbed driver, this event will only be
 * posted at main driver.
 *
 * <p>The event will only be posted in local mode or at lab side of remote mode.
 *
 * <p><b>NOTE</b>: Please <b>MOCK</b> this class in your unit tests rather than implementing it.
 *
 * @since lab server 4.31
 */
public interface LocalDriverStartingEvent extends DriverEvent {}
