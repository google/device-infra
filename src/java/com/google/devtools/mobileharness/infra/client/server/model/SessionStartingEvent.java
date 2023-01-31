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

package com.google.devtools.mobileharness.infra.client.server.model;

/**
 * An event that indicates that the session is starting.
 *
 * <p>In a subscriber method of the event, the job list of the session can be modified. After the
 * event, the session runner will start all jobs of the session.
 */
public class SessionStartingEvent extends SessionEvent {

  public SessionStartingEvent(SessionInfo sessionInfo) {
    super(sessionInfo);
  }
}
