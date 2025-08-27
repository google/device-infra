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

package android.telephony.utility;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;

/**
 * Utility to send information to the instrumentation. This ensure that the format used to send the
 * information is understood by the infrastructure side.
 */
public class SendToInstrumentation {

  /**
   * Metrics will be reported under the "status in progress" for test cases to be associated with
   * the running use cases.
   */
  public static final int INST_STATUS_IN_PROGRESS = 2;

  /**
   * Send a file to be logged in the instrumentation results with an expected format that the
   * infrastructure can understand.
   *
   * @param instru the current {@link Instrumentation}.
   * @param key The key under which to report the file.
   * @param file The file to be logged.
   */
  public static void sendFile(Instrumentation instru, String key, File file) {
    // TODO: include the file type in our output format for the host to read it.
    Bundle b = new Bundle();
    b.putString(key, file.getAbsolutePath());
    sendStatus(INST_STATUS_IN_PROGRESS, instru, b);
  }

  /**
   * Send a bundle of information to the instrumentation results.
   *
   * @param instru the current {@link Instrumentation}.
   * @param bundle the {@link Bundle} to be sent.
   */
  public static void sendBundle(Instrumentation instru, Bundle bundle) {
    sendStatus(INST_STATUS_IN_PROGRESS, instru, bundle);
  }

  /** Convenience method for {@link Instrumentation#sendStatus(int, Bundle)}. */
  private static void sendStatus(int code, Instrumentation instrumentation, Bundle bundle) {
    instrumentation.sendStatus(code, bundle);
  }
}
