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

package com.google.wireless.qa.mobileharness.shared.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;

/**
 * Util class for running sqlite3 commands with a Android devices. Notes sqlites3 commands only
 * works with rooted devices with API level >= 18.
 */
public class Sqlite {
  /** Adb shell template of sqlite sql command. Should be filled with the db name and sql. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_SQLITE_SQL = "sqlite3 %s.db '%s'";

  /** GService db PATH. */
  @VisibleForTesting
  static final String GSERVIE_DB = "/data/data/com.google.android.gsf/databases/gservices";

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  /** Creates a util for executing sqlite3 commands. */
  public Sqlite() {
    this(new Adb());
  }

  /** Creates a util for executing sqlite3 commands. */
  @VisibleForTesting
  Sqlite(Adb adb) {
    this.adb = adb;
  }

  /**
   * Run GServices Android ID(go/android-id). Only works with rooted devices with API level >= 18.
   */
  public String getGServicesAndroidID(String serial)
      throws MobileHarnessException, InterruptedException {
    return runGservicesSql(serial, "SELECT value FROM main WHERE name = \"android_id\"");
  }

  /** Run SQL against GServices db. Only works with rooted devices with API level >= 18. */
  private String runGservicesSql(String serial, String sql)
      throws MobileHarnessException, InterruptedException {
    return runSql(serial, GSERVIE_DB, sql);
  }

  /** Run SQL command. Only works with rooted devices with API level >= 18. */
  @VisibleForTesting
  String runSql(String serial, String dbPath, String sql)
      throws MobileHarnessException, InterruptedException {
    String output = adb.runShell(serial, String.format(ADB_SHELL_TEMPLATE_SQLITE_SQL, dbPath, sql));
    if (output.startsWith("Error:")) {
      throw new MobileHarnessException(
          ErrorCode.ANDROID_SQLITE_ERROR,
          String.format("Failed to run [%s] with db [%s]: %s", sql, dbPath, output));
    }
    return output;
  }
}
