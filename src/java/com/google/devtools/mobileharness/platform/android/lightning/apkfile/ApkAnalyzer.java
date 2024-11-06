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

package com.google.devtools.mobileharness.platform.android.lightning.apkfile;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.parser.XmlTreeAttributeParser;
import com.google.devtools.mobileharness.platform.android.parser.XmlTreeAttributeParser.AttributeHandler;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** APK analyzer for analyzing metadata in APK files. */
public class ApkAnalyzer {

  /**
   * Example:
   *
   * <pre>
   *       A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.test.runner"
   * (Raw: "android.test.runner")
   * </pre>
   *
   * The pattern has a named-capturing group "runner".
   */
  private static final Pattern XMLTREE_INSTRUMENTATION_TEST_RUNNER_PATTERN =
      Pattern.compile(
          "^\\s*A:\\s*http://schemas.android.com/apk/res/android:name\\(0x[0-9a-f]+\\)=\".+?\"\\s*\\(Raw:\\s*\"(?<runner>.+?)\"\\)$");

  private final Aapt aapt;

  public ApkAnalyzer() {
    this(new Aapt());
  }

  @VisibleForTesting
  ApkAnalyzer(Aapt aapt) {
    this.aapt = aapt;
  }

  /** Lists instrumentation test runner class names in an instrumentation test apk. */
  public List<String> getTestApkTestRunnerClassName(String testApkPath)
      throws MobileHarnessException, InterruptedException {
    String manifest = aapt.getAndroidManifest(testApkPath);
    List<String> result = new ArrayList<>();
    XmlTreeAttributeParser.newBuilder()
        .addHandler(
            AttributeHandler.newBuilder()
                .setElementType("instrumentation")
                .setAttributePattern(XMLTREE_INSTRUMENTATION_TEST_RUNNER_PATTERN)
                .setOnMatch(matcher -> result.add(matcher.group("runner")))
                .build())
        .build()
        .parse(manifest);
    return result;
  }
}
