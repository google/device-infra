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

package com.google.devtools.mobileharness.shared.util.failureanalyzer;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.failureanalyzer.FailureAnalyzer.AnalysisPattern;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
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
public class FailureAnalyzerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;

  private FailureAnalyzer failureAnalyzer;
  private Path genFileDir;
  private Params params;

  @Before
  public void setUp() throws Exception {
    failureAnalyzer = new FailureAnalyzer();
    genFileDir = temporaryFolder.newFolder("gen").toPath();

    when(testInfo.getGenFileDir()).thenReturn(genFileDir.toString());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    params = new Params(new Timing());
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
  }

  @Test
  public void analyze_standardPattern_foundInFile() throws Exception {
    Files.writeString(
        genFileDir.resolve(FailureAnalyzer.COBALT_LOG),
        "some line\nbeginning of crash\nother line");

    Optional<String> finding = failureAnalyzer.analyze(testInfo);

    assertThat(finding).hasValue("Some crash found in cobalt.log");
  }

  @Test
  public void analyze_multiLineRegex_foundInFile() throws Exception {
    AnalysisPattern multiLinePattern =
        new AnalysisPattern(
            "test.log", Pattern.compile("(?s)error A.*error B"), "Multi-line pattern match");
    failureAnalyzer = new FailureAnalyzer(ImmutableList.of(multiLinePattern));

    Files.writeString(
        genFileDir.resolve("test.log"), "line 1: error A\nline 2: some noise\nline 3: error B");

    Optional<String> finding = failureAnalyzer.analyze(testInfo);

    assertThat(finding).hasValue("Multi-line pattern match");
  }

  @Test
  public void analyze_multiLineRegex_missingPart() throws Exception {
    AnalysisPattern multiLinePattern =
        new AnalysisPattern(
            "test.log", Pattern.compile("(?s)error A.*error B"), "Multi-line pattern match");
    failureAnalyzer = new FailureAnalyzer(ImmutableList.of(multiLinePattern));

    Files.writeString(genFileDir.resolve("test.log"), "line 1: error A\nline 2: some noise");

    Optional<String> finding = failureAnalyzer.analyze(testInfo);

    assertThat(finding).isEmpty();
  }

  @Test
  public void analyze_standardPattern_foundInMemory() throws Exception {
    Log log = new Log(new Timing());
    when(testInfo.log()).thenReturn(log);
    log.append("beginning of crash in memory");

    Optional<String> finding = failureAnalyzer.analyze(testInfo);

    assertThat(finding).isEmpty();
  }

  @Test
  public void analyze_noMatch() throws Exception {
    Files.writeString(genFileDir.resolve(FailureAnalyzer.COBALT_LOG), "clean log");

    Optional<String> finding = failureAnalyzer.analyze(testInfo);

    assertThat(finding).isEmpty();
  }

  @Test
  public void analyze_fileMissing() throws Exception {
    // No files created in genFileDir.
    Optional<String> finding = failureAnalyzer.analyze(testInfo);
    assertThat(finding).isEmpty();
  }
}
