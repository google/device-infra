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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ElementAttribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ResultElement;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.ExtensionRegistry;
import java.io.File;
import java.io.FileInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XmlResultFormatterTest {

  private static final String TEST_SUMMARY1_YAML =
      TestRunfilesUtil.getRunfilesLocation("result/xml/testdata/test_summary1.yaml");
  private static final String TEST_SUMMARY2_YAML =
      TestRunfilesUtil.getRunfilesLocation("result/xml/testdata/test_summary2.yaml");
  private static final String EXPECTED_XML_FOR_TEST_SUMMARY_1N2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/xml/testdata/expected_xml_for_test_summary_1n2.xml");

  public static final Splitter LINE_SPLITTER = Splitter.onPattern("\r\n|\n|\r");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private LocalFileUtil localFileUtil;

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();
  private XmlResultFormatter xmlResultFormatter;

  @Before
  public void setUp() {
    xmlResultFormatter = new XmlResultFormatter(new MoblyYamlParser(), localFileUtil);
  }

  @Test
  public void writeMoblyResults() throws Exception {
    File xmlResultDir = temporaryFolder.newFolder("xml_result");
    MoblyResultInfo moblyResultInfo =
        MoblyResultInfo.of(
            ImmutableMap.of(
                "mobly_test_module_1",
                TEST_SUMMARY1_YAML,
                "mobly_test_module_2",
                TEST_SUMMARY2_YAML),
            ImmutableMap.of(
                "result_attr1", "result_attr1_value", "result_attr2", "result_attr2_value"),
            ImmutableMap.of(
                "build_attr1", "build_attr1_value", "build_attr2", "build_attr2_value"));

    xmlResultFormatter.writeMoblyResults(moblyResultInfo, xmlResultDir.getPath());

    verify(localFileUtil).prepareDir(xmlResultDir.getPath());
    assertThat(
            replaceLineBreak(
                realLocalFileUtil
                    .readFile(
                        xmlResultDir.toPath().resolve(XmlResultFormatter.TEST_RESULT_FILE_NAME))
                    .trim()))
        .isEqualTo(
            replaceLineBreak(realLocalFileUtil.readFile(EXPECTED_XML_FOR_TEST_SUMMARY_1N2).trim()));

    try (FileInputStream fis =
        new FileInputStream(
            xmlResultDir.toPath().resolve(XmlResultFormatter.TEST_RESULT_PB_FILE_NAME).toFile())) {
      ResultElement resultEl =
          ResultElement.newBuilder().mergeFrom(fis, ExtensionRegistry.newInstance()).build();
      assertThat(resultEl.getAttributeList())
          .containsExactly(
              ElementAttribute.newBuilder()
                  .setKey("result_attr1")
                  .setValue("result_attr1_value")
                  .build(),
              ElementAttribute.newBuilder()
                  .setKey("result_attr2")
                  .setValue("result_attr2_value")
                  .build());
      assertThat(resultEl.getBuildElement().getAttributeList()).hasSize(2);
      assertThat(resultEl.getSummaryElement().getAttributeList()).hasSize(4);
      assertThat(resultEl.getModuleElementList()).hasSize(2);
    }
  }

  @Test
  public void writeMoblyResults_noTestSummaryYamlFilesProvided_skip() throws Exception {
    File xmlResultDir = temporaryFolder.newFolder("xml_result_skip");
    MoblyResultInfo moblyResultInfo =
        MoblyResultInfo.of(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());

    xmlResultFormatter.writeMoblyResults(moblyResultInfo, xmlResultDir.getPath());

    verify(localFileUtil, never()).prepareDir(xmlResultDir.getPath());
  }

  private static String replaceLineBreak(String str) {
    return Joiner.on("\n").join(LINE_SPLITTER.omitEmptyStrings().splitToList(str));
  }
}
