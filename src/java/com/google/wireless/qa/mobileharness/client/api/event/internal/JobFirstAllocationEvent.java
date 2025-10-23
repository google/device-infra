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

package com.google.wireless.qa.mobileharness.client.api.event.internal;

import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * Fired when the first test of a job gets its first successful device allocation.
 *
 * <p>This event is posted to the plugin scopes of the job, currently just including {@link
 * JobRunner.EventScope#API_PLUGIN}.
 *
 * <p><b>IMPORTANT:</b> This event is posted synchronously and is blocking on the {@code
 * JobRunner}'s main thread. Subscribers should only perform light-weight operations in their
 * handler methods. Any time-consuming work should be delegated to a separate background thread to
 * avoid blocking the job's execution.
 *
 * @param allocatedTest the test that received the first allocation.
 */
public record JobFirstAllocationEvent(TestInfo allocatedTest) {}
