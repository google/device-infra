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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.Map;
import javax.inject.Inject;

/**
 * Merger for report-log JSON files ({@code *.reportlog.json}) across retry sessions.
 *
 * <p>When a module is retried on a subset of its test cases, the retry session produces a partial
 * report-log that only covers the retried tests. This merger combines the previous session's
 * report-log with the retry session's report-log using a structural merge:
 *
 * <ul>
 *   <li>JSON objects are merged key by key, recursively.
 *   <li>JSON arrays whose elements are all {@code {"key": ..., "value": ...}} entries (the JSON
 *       representation of proto {@code map} fields) are merged by key, recursively.
 *   <li>Any other JSON arrays are concatenated (previous entries first).
 *   <li>For anything else (strings, numbers, booleans, or mismatched types), the retry session's
 *       value overrides the previous one, unless the retry value is {@code null}.
 * </ul>
 */
public class RetryReportLogMerger {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String KEY_FIELD = "key";
  private static final String VALUE_FIELD = "value";

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

  private final LocalFileUtil localFileUtil;

  @Inject
  RetryReportLogMerger(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Merges the previous session's report-log JSON file into the retry session's report-log JSON
   * file, overwriting {@code retryFile} with the merged content.
   *
   * <p>If either file cannot be parsed as a JSON object, or both files have the same content,
   * {@code retryFile} is left unchanged.
   */
  public void mergeReportLogFiles(Path prevFile, Path retryFile) {
    try {
      JsonElement prev = JsonParser.parseString(localFileUtil.readFile(prevFile));
      JsonElement retry = JsonParser.parseString(localFileUtil.readFile(retryFile));
      if (!prev.isJsonObject() || !retry.isJsonObject()) {
        logger.atWarning().log(
            "Skip merging report log files %s and %s since not both are JSON objects.",
            prevFile, retryFile);
        return;
      }
      if (prev.equals(retry)) {
        // E.g. Tradefed's own retry already copied the previous file for an unretried module.
        // Merging would duplicate the array entries.
        return;
      }
      localFileUtil.writeToFile(retryFile.toString(), GSON.toJson(merge(prev, retry)));
    } catch (MobileHarnessException | JsonParseException e) {
      logger.atWarning().withCause(e).log(
          "Failed to merge retry report log file %s with previous file %s.", retryFile, prevFile);
    }
  }

  /**
   * Structurally merges {@code retry} on top of {@code prev}. See the class doc for the rules. The
   * result may share elements with the inputs.
   */
  static JsonElement merge(JsonElement prev, JsonElement retry) {
    if (prev.isJsonObject() && retry.isJsonObject()) {
      return mergeObjects(prev.getAsJsonObject(), retry.getAsJsonObject());
    }
    if (prev.isJsonArray() && retry.isJsonArray()) {
      return mergeArrays(prev.getAsJsonArray(), retry.getAsJsonArray());
    }
    // A null in the retry means "not set" (proto JSON parsers skip it), so keep the previous value.
    return retry.isJsonNull() ? prev : retry;
  }

  private static JsonObject mergeObjects(JsonObject prev, JsonObject retry) {
    JsonObject merged = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : prev.entrySet()) {
      merged.add(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, JsonElement> entry : retry.entrySet()) {
      String key = entry.getKey();
      merged.add(
          key, merged.has(key) ? merge(merged.get(key), entry.getValue()) : entry.getValue());
    }
    return merged;
  }

  private static JsonArray mergeArrays(JsonArray prev, JsonArray retry) {
    if (isKeyValueList(prev) && isKeyValueList(retry)) {
      return toKeyValueList(mergeObjects(toObject(prev), toObject(retry)));
    }
    JsonArray merged = new JsonArray();
    merged.addAll(prev);
    merged.addAll(retry);
    return merged;
  }

  /**
   * Returns whether the array is non-empty and only contains {@code {"key": ..., "value": ...}}.
   */
  private static boolean isKeyValueList(JsonArray array) {
    if (array.isEmpty()) {
      return false;
    }
    for (JsonElement element : array) {
      if (!element.isJsonObject()) {
        return false;
      }
      JsonObject entry = element.getAsJsonObject();
      if (entry.size() != 2 || !entry.has(VALUE_FIELD) || !isString(entry.get(KEY_FIELD))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isString(JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
  }

  /** Converts a key/value list to an object; a later duplicate key replaces an earlier one. */
  private static JsonObject toObject(JsonArray keyValueList) {
    JsonObject object = new JsonObject();
    for (JsonElement element : keyValueList) {
      JsonObject entry = element.getAsJsonObject();
      object.add(entry.get(KEY_FIELD).getAsString(), entry.get(VALUE_FIELD));
    }
    return object;
  }

  private static JsonArray toKeyValueList(JsonObject object) {
    JsonArray array = new JsonArray();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      JsonObject keyValue = new JsonObject();
      keyValue.addProperty(KEY_FIELD, entry.getKey());
      keyValue.add(VALUE_FIELD, entry.getValue());
      array.add(keyValue);
    }
    return array;
  }
}
