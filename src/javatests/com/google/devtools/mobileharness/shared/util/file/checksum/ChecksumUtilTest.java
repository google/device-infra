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

package com.google.devtools.mobileharness.shared.util.file.checksum;

import static com.google.common.truth.Truth.assertThat;
import static org.apache.commons.lang3.CharEncoding.UTF_8;

import com.google.common.hash.Hashing;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ChecksumUtil}. */
@RunWith(JUnit4.class)
public final class ChecksumUtilTest {

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final Path TEST_DATA_DIR =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/"
                  + "shared/util/file/checksum/testdata/"));

  private static final Path SMALL_FILE =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/"
                  + "shared/util/file/checksum/testdata/Md5.small"));

  private static final Path EMPTY_FILE =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/"
                  + "shared/util/file/checksum/testdata/Md5.empty"));

  private final LocalFileUtil fileUtil = new LocalFileUtil();
  private final ChecksumUtil util = new ChecksumUtil(Hashing.murmur3_128());
  private Path homeDir;

  @Before
  public void setUp() throws Exception {
    homeDir = Paths.get(tmpFolder.getRoot().getAbsolutePath());
  }

  @Test
  public void testFingerprint_runTwiceForFile_returnConsistent() throws Exception {
    String hashCode = util.fingerprint(SMALL_FILE);

    for (int i = 0; i < 2; i++) {
      assertThat(util.fingerprint(SMALL_FILE)).isEqualTo(hashCode);
    }
  }

  @Test
  public void testFingerprint_runTwiceForDir_returnConsistent() throws Exception {
    String hashCode = util.fingerprint(TEST_DATA_DIR);

    for (int i = 0; i < 2; i++) {
      assertThat(util.fingerprint(TEST_DATA_DIR)).isEqualTo(hashCode);
    }
  }

  @Test
  public void testFingerprint_runForFile_returnValidMd5() throws Exception {
    ChecksumUtil md5Util = new ChecksumUtil(Hashing.md5());
    assertThat(md5Util.fingerprint(EMPTY_FILE)).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    assertThat(md5Util.fingerprint(SMALL_FILE)).isEqualTo("3ed29bde24a6a8497ac8f3925bb03237");
    assertThat(md5Util.fingerprint(TEST_DATA_DIR)).isEqualTo("d86efc3943f26a60b1d48529dcb86b31");
  }

  @Test
  public void testFingerprint_runForFile_returnValidMurmur3_128() throws Exception {
    assertThat(util.fingerprint(EMPTY_FILE)).isEqualTo("00000000000000000000000000000000");
    assertThat(util.fingerprint(SMALL_FILE)).isEqualTo("efeb5e72d8f8ed1a7f787f8a70895d35");
    assertThat(util.fingerprint(TEST_DATA_DIR)).isEqualTo("c9462864b948810f764d81c57f515dba");
  }

  @Test
  public void testFingerprint_runForString_returnMurmur3_128() throws Exception {
    assertThat(util.fingerprintStr("")).isEqualTo("00000000000000000000000000000000");
    assertThat(util.fingerprintStr("aa")).isEqualTo("6fa3fb04347141b739464d9c42fb7098");
    assertThat(util.fingerprintStr("abcädef")).isEqualTo("104328d599032887c82a48b32ef9f2c5");
  }

  @Test
  public void testFingerPrint_longString() throws Exception {
    String s = "a".repeat(1000000000);
    Instant start = Clock.systemUTC().instant();
    var unused = util.fingerprintStr(s);
    Instant end = Clock.systemUTC().instant();
    assertThat(Duration.between(start, end)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  public void testFinglerPrint_fileInputEqualBytesInput() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.writeToFile(file.toString(), "aa");

    assertThat(util.fingerprint(file)).isEqualTo("6fa3fb04347141b739464d9c42fb7098");
    assertThat(util.fingerprintBytesHashCode("aa".getBytes(UTF_8)).toString())
        .isEqualTo("6fa3fb04347141b739464d9c42fb7098");
  }

  @Test
  public void testFingerprint_fileIsModified() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.writeToFile(file.toString(), "aa");
    assertThat(util.fingerprint(file)).isEqualTo("6fa3fb04347141b739464d9c42fb7098");
    assertThat(util.fingerprintBytesHashCode("aa".getBytes(UTF_8)).toString())
        .isEqualTo("6fa3fb04347141b739464d9c42fb7098");

    fileUtil.removeFileOrDir(file.toString());
    fileUtil.writeToFile(file.toString(), "abcädef");
    assertThat(util.fingerprint(file)).isEqualTo("104328d599032887c82a48b32ef9f2c5");
    assertThat(util.fingerprintBytesHashCode("abcädef".getBytes(UTF_8)).toString())
        .isEqualTo("104328d599032887c82a48b32ef9f2c5");
  }

  @Test
  public void testFingerprint_directoryIsModified() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.prepareDir(file.toString());
    fileUtil.writeToFile(file + "/Md5.small", fileUtil.readFile(SMALL_FILE));
    fileUtil.writeToFile(file + "/Md5.empty", fileUtil.readFile(EMPTY_FILE));
    assertThat(util.fingerprint(file)).isEqualTo("c9462864b948810f764d81c57f515dba");

    // Add a short delay because if file's two modification times are very very close (at microsec
    // level), it will get fingerprint from cache at the latter call and cause test flaky.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(2));
    fileUtil.writeToFile(file + "/Md5.empty", "aa");
    assertThat(util.fingerprint(file)).isEqualTo("508dbabd27b77402dc9865b17442176d");
  }
}
