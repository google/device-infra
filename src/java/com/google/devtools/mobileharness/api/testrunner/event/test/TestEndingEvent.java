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
 * Event which signals that the ending process of a test is going to start.
 *
 * <p><b>NOTE</b>: Please <b>MOCK</b> this class in your unit tests rather than implementing it.
 *
 * @since lab server 4.32
 */
public interface TestEndingEvent extends TestEvent, ExecutionEndEvent {}
