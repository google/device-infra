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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.parser.XmlTreeAttributeParser;
import com.google.devtools.mobileharness.platform.android.parser.XmlTreeAttributeParser.AttributeHandler;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** APK analyzer for analyzing metadata in APK files. */
public class ApkAnalyzer {

  /**
   * Metadata parsed from an {@code <instrumentation>} element in {@code AndroidManifest.xml}.
   *
   * @param name value of {@code android:name} (expanded with the test package name if it starts
   *     with {@code "."}), or empty string if absent
   * @param targetPackage value of {@code android:targetPackage}, or empty string if absent
   */
  public record InstrumentationInfo(String name, String targetPackage) {
    public InstrumentationInfo {
      requireNonNull(name);
      requireNonNull(targetPackage);
    }
  }

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

  private static final Pattern XMLTREE_INSTRUMENTATION_TARGET_PKG_PATTERN =
      Pattern.compile("^\\s*A:.*android:targetPackage\\(\\w+\\)=\"([^\"]+)\".*");

  private static final Pattern XMLTREE_INSTRUMENTATION_NAME_PATTERN =
      Pattern.compile("^\\s*A:.*android:name\\(\\w+\\)=\"([^\"]+)\".*");

  private final Aapt aapt;

  public ApkAnalyzer() {
    this(new Aapt());
  }

  @Inject
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

  /** Parses {@code <instrumentation>} elements from the {@code AndroidManifest.xml} of an APK. */
  public ImmutableList<InstrumentationInfo> parseInstrumentation(
      String testApkPath, String testPackageName)
      throws MobileHarnessException, InterruptedException {
    String xmlTree = aapt.getAndroidManifest(testApkPath);
    ImmutableList.Builder<InstrumentationInfo> infos = ImmutableList.builder();
    boolean inInstrumentation = false;
    String currentName = "";
    String currentTargetPackage = "";

    for (String line : Splitters.LINE_SPLITTER.split(xmlTree)) {
      String trimmedLine = line.trim();

      if (trimmedLine.startsWith("E: ")) {
        if (inInstrumentation) {
          infos.add(new InstrumentationInfo(currentName, currentTargetPackage));
          inInstrumentation = false;
        }

        String[] parts = trimmedLine.split("\\s+", 3);
        if (parts.length >= 2 && parts[1].equals("instrumentation")) {
          inInstrumentation = true;
          currentName = "";
          currentTargetPackage = "";
        }
        continue;
      }

      if (inInstrumentation) {
        Matcher nameMatcher = XMLTREE_INSTRUMENTATION_NAME_PATTERN.matcher(line);
        if (nameMatcher.matches()) {
          String name = nameMatcher.group(1);
          if (name.startsWith(".")) {
            name = testPackageName + name;
          }
          currentName = name;
        } else {
          Matcher targetMatcher = XMLTREE_INSTRUMENTATION_TARGET_PKG_PATTERN.matcher(line);
          if (targetMatcher.matches()) {
            currentTargetPackage = targetMatcher.group(1);
          }
        }
      }
    }

    if (inInstrumentation) {
      infos.add(new InstrumentationInfo(currentName, currentTargetPackage));
    }
    return infos.build();
  }
}
