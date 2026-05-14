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

package com.google.wireless.qa.mobileharness.shared.api.decorator.util;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for reformatting and merging JSON report logs. */
public final class ReportLogCollectorUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern TEST_METRICS_PATTERN =
      Pattern.compile("\\\"([a-z0-9_]*)\\\":(\\{[^{}]*\\})");

  private ReportLogCollectorUtil() {}

  public static void pullFromHost(
      File src, File dest, LocalFileUtil localFileUtil, TestInfo testInfo) {
    try {
      if (src.listFiles() != null) {
        for (File srcReportLog : src.listFiles()) {
          File destReportLog = new File(dest, srcReportLog.getName());
          merge(srcReportLog, destReportLog, localFileUtil);
        }
      }
      localFileUtil.removeFileOrDir(src.getAbsolutePath());
    } catch (JsonSyntaxException | MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.SEVERE)
          .alsoTo(logger)
          .withCause(e)
          .log("Caught exception during pull.");
    } catch (InterruptedException e) {
      testInfo.log().at(Level.SEVERE).alsoTo(logger).withCause(e).log("Interrupted during pull.");
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Reformats test metrics JSONs to convert multiple JSON objects with identical stream names into
   * arrays of objects.
   *
   * @param resultDir the directory containing test metrics
   * @param localFileUtil the utility for local file operations
   */
  public static void reformatRepeatedStreams(
      File resultDir, LocalFileUtil localFileUtil, TestInfo testInfo) {
    try {
      if (resultDir.listFiles() != null) {
        File[] reportLogs = resultDir.listFiles();
        for (File reportLog : reportLogs) {
          String content =
              String.join("", localFileUtil.readLineListFromFile(reportLog.getAbsolutePath()));
          localFileUtil.writeToFile(reportLog.getAbsolutePath(), reformatJsonString(content));
        }
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.SEVERE)
          .alsoTo(logger)
          .withCause(e)
          .log("Caught exception during reformatting.");
    }
  }

  /**
   * Helper function to reformat JSON string.
   *
   * @param jsonString the JSON string to be reformatted
   * @return the reformatted JSON string
   */
  public static String reformatJsonString(String jsonString) {
    StringBuilder newJsonBuilder = new StringBuilder();
    HashMap<String, List<String>> jsonMap = new HashMap<>();
    Matcher m = TEST_METRICS_PATTERN.matcher(jsonString);
    if (!m.find()) {
      return jsonString;
    }
    do {
      String key = m.group(1);
      String value = m.group(2);
      jsonMap.computeIfAbsent(key, (String k) -> new ArrayList<String>());
      jsonMap.get(key).add(value);
    } while (m.find());
    newJsonBuilder.append("{");
    boolean firstLine = true;
    for (String key : jsonMap.keySet()) {
      if (!firstLine) {
        newJsonBuilder.append(",");
      } else {
        firstLine = false;
      }
      newJsonBuilder.append("\"").append(key).append("\":[");
      boolean firstValue = true;
      for (String stream : jsonMap.get(key)) {
        if (!firstValue) {
          newJsonBuilder.append(",");
        } else {
          firstValue = false;
        }
        newJsonBuilder.append(stream);
      }
      newJsonBuilder.append("]");
    }
    newJsonBuilder.append("}");
    return newJsonBuilder.toString();
  }

  /**
   * A helper method that merges a JSON file's contents to a local file.
   *
   * @param origFile the original file to be copied
   * @param destFile the destination file
   * @param localFileUtil the utility for local file operations
   * @throws MobileHarnessException if file operations fail
   */
  public static void merge(File origFile, File destFile, LocalFileUtil localFileUtil)
      throws MobileHarnessException {
    String origContent =
        String.join("", localFileUtil.readLineListFromFile(origFile.getAbsolutePath()));
    JsonObject mergedJson = JsonParser.parseString(origContent).getAsJsonObject();

    if (destFile.exists()) {
      String destContent =
          String.join("", localFileUtil.readLineListFromFile(destFile.getAbsolutePath()));
      JsonObject destFileJson = JsonParser.parseString(destContent).getAsJsonObject();

      for (Map.Entry<String, JsonElement> entry : destFileJson.entrySet()) {
        String stream = entry.getKey();
        JsonArray testResults = entry.getValue().getAsJsonArray();

        if (mergedJson.has(stream)) {
          JsonArray mergedArray = mergedJson.get(stream).getAsJsonArray();
          for (JsonElement testResult : testResults) {
            mergedArray.add(testResult);
          }
        } else {
          mergedJson.add(stream, testResults);
        }
      }
    }
    localFileUtil.writeToFile(destFile.getAbsolutePath(), mergedJson.toString());
  }
}
