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
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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

  private static final String TEST_DATA_ROOT_PATH =
      "com_google_deviceinfra/src/javatests/com/google/devtools/mobileharness/"
          + "shared/util/file/checksum/testdata/";

  private static final Path TEST_DATA_DIR = Paths.get(getRunfilesLocation(""));

  private static final Path SMALL_FILE = Paths.get(getRunfilesLocation("Md5.small"));

  private static final Path EMPTY_FILE = Paths.get(getRunfilesLocation("Md5.empty"));

  private final LocalFileUtil fileUtil = new LocalFileUtil();
  private final ChecksumUtil util = new ChecksumUtil();
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
  public void testFingerprint_runForFile_returnValidCrc32() throws Exception {
    assertThat(util.fingerprint(EMPTY_FILE)).isEqualTo("00000000");
    assertThat(util.fingerprint(SMALL_FILE)).isEqualTo("3d720930");
    assertThat(util.fingerprint(TEST_DATA_DIR)).isEqualTo("963d67af");
  }

  @Test
  public void testFingerprint_runForString_returnCrc32() throws Exception {
    assertThat(util.fingerprintStr("")).isEqualTo("00000000");
    assertThat(util.fingerprintStr("aa")).isEqualTo("d7198a07");
    assertThat(util.fingerprintStr("abcädef")).isEqualTo("5e7b1374");
  }

  @Test
  public void testFingerprint_runForString_returnMd5() throws Exception {
    ChecksumUtil md5Util = new ChecksumUtil(Hashing.md5());
    assertThat(md5Util.fingerprintStr("")).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    assertThat(md5Util.fingerprintStr("aa")).isEqualTo("4124bc0a9335c27f086f24ba207a4912");
    assertThat(md5Util.fingerprintStr("abcädef")).isEqualTo("2a2494d3760d4c6906cdf133bca3cb9e");
  }

  @Test
  public void testFinglerPrint_fileInputEqualBytesInput() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.writeToFile(file.toString(), "aa");

    assertThat(util.fingerprint(file)).isEqualTo("d7198a07");
    assertThat(util.fingerprintBytesHashCode("aa".getBytes(UTF_8)).toString())
        .isEqualTo("d7198a07");
  }

  @Test
  public void testFingerprint_fileIsModified() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.writeToFile(file.toString(), "aa");
    assertThat(util.fingerprint(file)).isEqualTo("d7198a07");
    assertThat(util.fingerprintBytesHashCode("aa".getBytes(UTF_8)).toString())
        .isEqualTo("d7198a07");

    fileUtil.removeFileOrDir(file.toString());
    fileUtil.writeToFile(file.toString(), "abcädef");
    assertThat(util.fingerprint(file)).isEqualTo("5e7b1374");
    assertThat(util.fingerprintBytesHashCode("abcädef".getBytes(UTF_8)).toString())
        .isEqualTo("5e7b1374");
  }

  @Test
  public void testFingerprint_directoryIsModified() throws Exception {
    Path file = homeDir.resolve("src");
    fileUtil.prepareDir(file.toString());
    fileUtil.writeToFile(file + "/Md5.small", fileUtil.readFile(SMALL_FILE));
    fileUtil.writeToFile(file + "/Md5.empty", fileUtil.readFile(EMPTY_FILE));
    assertThat(util.fingerprint(file)).isEqualTo("963d67af");

    // Add a short delay because if file's two modification times are very very close (at microsec
    // level), it will get fingerprint from cache at the latter call and cause test flaky.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(2));
    fileUtil.writeToFile(file + "/Md5.empty", "aa");
    assertThat(util.fingerprint(file)).isEqualTo("fe6765c7");
  }

  private static String getRunfilesLocation(String suffix) {
    try {
      return com.google.devtools.build.runfiles.Runfiles.create()
          .rlocation(TEST_DATA_ROOT_PATH + suffix);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }
}
