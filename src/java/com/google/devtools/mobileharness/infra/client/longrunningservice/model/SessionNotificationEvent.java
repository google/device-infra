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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugStringWithPrinter;

import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.protobuf.TextFormat.Printer;

/** An event that indicates the session has received a notification from client. */
public class SessionNotificationEvent extends SessionEvent {

  private final SessionNotification sessionNotification;
  private final Printer protoPrinter;

  public SessionNotificationEvent(
      SessionInfo sessionInfo, SessionNotification sessionNotification, Printer protoPrinter) {
    super(sessionInfo);
    this.sessionNotification = sessionNotification;
    this.protoPrinter = protoPrinter;
  }

  /** A notification from client. */
  public SessionNotification sessionNotification() {
    return sessionNotification;
  }

  @Override
  public String toString() {
    return String.format(
        "%s%s",
        super.toString(),
        String.format(
            " with notification [%s]",
            shortDebugStringWithPrinter(sessionNotification, protoPrinter)));
  }
}
