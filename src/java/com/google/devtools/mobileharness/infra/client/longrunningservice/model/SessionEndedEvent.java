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

package com.google.devtools.mobileharness.infra.client.longrunningservice.model;

import java.util.Optional;
import javax.annotation.Nullable;

/** An event that indicates the session has ended and all jobs of it have ended. */
public class SessionEndedEvent extends SessionEvent {

  private final Throwable sessionError;

  public SessionEndedEvent(SessionInfo sessionInfo, @Nullable Throwable sessionError) {
    super(sessionInfo);
    this.sessionError = sessionError;
  }

  /** Error occurred during the session execution, if any. */
  public Optional<Throwable> sessionError() {
    return Optional.ofNullable(sessionError);
  }

  @Override
  public String toString() {
    return String.format(
        "%s%s",
        super.toString(),
        sessionError().map(error -> String.format(" with session_error [%s]", error)).orElse(""));
  }
}
