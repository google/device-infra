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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RetryReportLogMergerTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private final LocalFileUtil localFileUtil = new LocalFileUtil();
  private final RetryReportLogMerger merger = new RetryReportLogMerger(localFileUtil);

  @Test
  public void merge_scalars_retryOverridesUnlessNull() {
    assertThat(
            merge(
                "{\"a\": \"old\", \"b\": 1, \"c\": true, \"d\": \"kept\", \"e\": [1]}",
                "{\"a\": \"new\", \"b\": 2, \"d\": null, \"e\": \"str\"}"))
        .isEqualTo(
            parse("{\"a\": \"new\", \"b\": 2, \"c\": true, \"d\": \"kept\", \"e\": \"str\"}"));
  }

  @Test
  public void merge_nestedObjects_mergedRecursively() {
    assertThat(
            merge(
                "{\"info\": {\"start\": \"s1\", \"end\": \"e1\"}, \"only_prev\": {\"x\": 1}}",
                "{\"info\": {\"end\": \"e2\", \"extra\": \"v\"}, \"only_retry\": {\"y\": 2}}"))
        .isEqualTo(
            parse(
                "{\"info\": {\"start\": \"s1\", \"end\": \"e2\", \"extra\": \"v\"},"
                    + " \"only_prev\": {\"x\": 1}, \"only_retry\": {\"y\": 2}}"));
  }

  @Test
  public void merge_plainArrays_concatenatedWithPrevFirst() {
    assertThat(merge("{\"stream\": [{\"v\": 1}, {\"v\": 2}]}", "{\"stream\": [{\"v\": 3}]}"))
        .isEqualTo(parse("{\"stream\": [{\"v\": 1}, {\"v\": 2}, {\"v\": 3}]}"));
  }

  @Test
  public void merge_keyValueLists_mergedByKeyRecursively() {
    String prev =
        """
        {
          "test_classes": [
            {
              "key": "UnretriedClass",
              "value": {"scenarios": [{"key": "s1", "value": {"med": 1.0}}]}
            },
            {
              "key": "RetriedClass",
              "value": {
                "test_info": {"start_time": "t1", "end_time": "t2"},
                "scenarios": [
                  {"key": "kept", "value": {"med": 10.0, "iterations": [{"i": 1}]}},
                  {"key": "retried", "value": {"med": 5.0, "iterations": [{"i": 1}]}}
                ]
              }
            }
          ],
          "wifi_chipset": "WCN7861"
        }
        """;
    String retry =
        """
        {
          "test_classes": [
            {
              "key": "RetriedClass",
              "value": {
                "test_info": {"start_time": "t3", "end_time": "t4"},
                "scenarios": [
                  {"key": "retried", "value": {"med": 50.0, "iterations": [{"i": 2}]}},
                  {"key": "added", "value": {"med": 7.0}}
                ]
              }
            }
          ],
          "wifi_chipset": "NA"
        }
        """;
    String expected =
        """
        {
          "test_classes": [
            {
              "key": "UnretriedClass",
              "value": {"scenarios": [{"key": "s1", "value": {"med": 1.0}}]}
            },
            {
              "key": "RetriedClass",
              "value": {
                "test_info": {"start_time": "t3", "end_time": "t4"},
                "scenarios": [
                  {"key": "kept", "value": {"med": 10.0, "iterations": [{"i": 1}]}},
                  {"key": "retried", "value": {"med": 50.0, "iterations": [{"i": 1}, {"i": 2}]}},
                  {"key": "added", "value": {"med": 7.0}}
                ]
              }
            }
          ],
          "wifi_chipset": "NA"
        }
        """;

    assertThat(merge(prev, retry)).isEqualTo(parse(expected));
  }

  @Test
  public void merge_arraysThatAreNotExactKeyValueEntries_concatenated() {
    // Records with extra fields are not map entries; merging by key would drop the other fields.
    assertThat(
            merge(
                "{\"r\": [{\"key\": \"a\", \"label\": \"x\"}]}",
                "{\"r\": [{\"key\": \"a\", \"label\": \"y\"}]}"))
        .isEqualTo(
            parse(
                "{\"r\": [{\"key\": \"a\", \"label\": \"x\"}, {\"key\": \"a\", \"label\":"
                    + " \"y\"}]}"));
    // A non-string key must not reach the by-key merge (which calls getAsString()).
    assertThat(
            merge(
                "{\"m\": [{\"key\": \"a\", \"value\": 1}]}",
                "{\"m\": [{\"key\": {\"x\": 1}, \"value\": 2}]}"))
        .isEqualTo(
            parse(
                "{\"m\": [{\"key\": \"a\", \"value\": 1}, {\"key\": {\"x\": 1}, \"value\": 2}]}"));
    assertThat(merge("{\"m\": []}", "{\"m\": [{\"key\": \"a\", \"value\": 1}]}"))
        .isEqualTo(parse("{\"m\": [{\"key\": \"a\", \"value\": 1}]}"));
  }

  @Test
  public void mergeReportLogFiles_writesMergedJsonToRetryFile() throws Exception {
    String merged =
        mergeFiles(
            "{\"streams\": [{\"v\": 1}], \"name\": \"old\", \"keep\": \"a=b&c<d>\"}",
            "{\"streams\": [{\"v\": 2}], \"name\": \"new\"}");

    assertThat(merged)
        .isEqualTo(
            """
            {
              "streams": [
                {
                  "v": 1
                },
                {
                  "v": 2
                }
              ],
              "name": "new",
              "keep": "a=b&c<d>"
            }\
            """);
  }

  @Test
  public void mergeReportLogFiles_identicalContent_leavesRetryFileUnchanged() throws Exception {
    String content = "{\"streams\": [{\"v\": 1}]}";
    assertThat(mergeFiles(content, content)).isEqualTo(content);
  }

  @Test
  public void mergeReportLogFiles_prevNotJsonObject_leavesRetryFileUnchanged() throws Exception {
    assertThat(mergeFiles("{not-valid-json", "{\"a\": 1}")).isEqualTo("{\"a\": 1}");
    assertThat(mergeFiles("[1, 2]", "{\"a\": 1}")).isEqualTo("{\"a\": 1}");
  }

  /** Writes both contents to files, merges them and returns the retry file content afterwards. */
  private String mergeFiles(String prevContent, String retryContent) throws Exception {
    Path prevFile = tmpFolder.newFolder().toPath().resolve("Module.reportlog.json");
    Path retryFile = tmpFolder.newFolder().toPath().resolve("Module.reportlog.json");
    localFileUtil.writeToFile(prevFile.toString(), prevContent);
    localFileUtil.writeToFile(retryFile.toString(), retryContent);

    merger.mergeReportLogFiles(prevFile, retryFile);

    return localFileUtil.readFile(retryFile);
  }

  private static JsonElement merge(String prevJson, String retryJson) {
    return RetryReportLogMerger.merge(parse(prevJson), parse(retryJson));
  }

  private static JsonElement parse(String json) {
    return JsonParser.parseString(json);
  }
}
