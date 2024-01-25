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

package com.google.devtools.mobileharness.infra.ats.console.util.notice;

/** Utility to create the notice message. */
public class NoticeMessageUtil {

  private static final String EXTERNAL_AGREEMENT = "https://opensource.google.com/docs/cla/";
  private static final String ANONYMOUS = "anonymous ";

  private static final String NOTICE_MESSAGE =
      "==================\nNotice:\n"
          + "We collect %susage statistics in accordance with our Content Licenses "
          + "(https://source.android.com/setup/start/licenses), Contributor License "
          + "Agreement (%s), Privacy Policy "
          + "(https://policies.google.com/privacy) and Terms of Service "
          + "(https://policies.google.com/terms)."
          + "\n==================";

  /** Returns the notice message. */
  public static String getNoticeMessage() {
    return getNoticeMessageOss();
  }

  private static String getNoticeMessageOss() {
    return String.format(NOTICE_MESSAGE, ANONYMOUS, EXTERNAL_AGREEMENT);
  }

  private NoticeMessageUtil() {}
}
