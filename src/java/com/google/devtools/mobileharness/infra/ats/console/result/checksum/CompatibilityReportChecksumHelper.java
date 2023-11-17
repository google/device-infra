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

package com.google.devtools.mobileharness.infra.ats.console.result.checksum;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * Helper to generate the checksum of the results and files. Use {@link #tryCreateChecksum(File,
 * Result, String)} to create the checksum file in the result dir.
 */
public class CompatibilityReportChecksumHelper {

  // Android 10 and newer
  // **** Keep in sync with [android] tradefed/result/suite/CertificationChecksumHelper ****
  @VisibleForTesting static final String NAME = "checksum-suite.data";

  private static final double DEFAULT_FPP = 0.05;
  private static final String SEPARATOR = "/";

  // Serialized format Id (ie magic number) used to identify serialized data.
  private static final short SERIALIZED_FORMAT_CODE = 650;
  private static final short CURRENT_VERSION = 1;

  private final BloomFilter<CharSequence> resultChecksum;
  private final HashMap<String, byte[]> fileChecksum;
  private final short version;
  private final String buildFingerprint;

  /**
   * Creates new instance of {@link CompatibilityReportChecksumHelper}.
   *
   * @param testCount the number of test results that will be stored
   * @param fpp the false positive probability for result lookup misses
   * @param version the version of the checksum
   * @param buildFingerprint build fingerprint for the device under test
   */
  private CompatibilityReportChecksumHelper(
      int testCount, double fpp, short version, String buildFingerprint) {
    resultChecksum = BloomFilter.create(Funnels.unencodedCharsFunnel(), testCount, fpp);
    fileChecksum = new HashMap<>();
    this.version = version;
    this.buildFingerprint = buildFingerprint;
  }

  /**
   * Deserializes checksum from file.
   *
   * @param directory the parent directory containing the checksum file
   * @throws ChecksumValidationException if get errors in deserializing the checksum file
   */
  @VisibleForTesting
  CompatibilityReportChecksumHelper(File directory, String buildFingerprint)
      throws ChecksumValidationException {
    this.buildFingerprint = buildFingerprint;
    File file = new File(directory, NAME);
    try (FileInputStream fileStream = new FileInputStream(file);
        InputStream outputStream = new BufferedInputStream(fileStream);
        ObjectInput objectInput = new ObjectInputStream(outputStream)) {
      short magicNumber = objectInput.readShort();
      switch (magicNumber) {
        case SERIALIZED_FORMAT_CODE:
          version = objectInput.readShort();
          Object bfObject = objectInput.readObject();
          if (!(bfObject instanceof BloomFilter<?>)) {
            throw new ChecksumValidationException(
                "Unexpected object, expected Bloom Filter; found " + bfObject.getClass().getName());
          }
          @SuppressWarnings("unchecked") // Safe by convention.
          BloomFilter<CharSequence> resultChecksumTmp = (BloomFilter<CharSequence>) bfObject;
          resultChecksum = resultChecksumTmp;

          Object hashObject = objectInput.readObject();
          if (!(hashObject instanceof HashMap<?, ?>)) {
            throw new ChecksumValidationException(
                "Unexpected object, expected HashMap; found " + hashObject.getClass().getName());
          }
          @SuppressWarnings("unchecked") // Safe by convention.
          HashMap<String, byte[]> fileChecksumTmp = (HashMap<String, byte[]>) hashObject;
          fileChecksum = fileChecksumTmp;
          break;
        default:
          throw new ChecksumValidationException("Unknown format of serialized data.");
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new ChecksumValidationException("Unable to load checksum from file.", e);
    }
    if (version > CURRENT_VERSION) {
      throw new ChecksumValidationException("File contains a newer version of ReportChecksum.");
    }
  }

  /**
   * Calculate checksum of test results and files in result directory and write to disk.
   *
   * @param dir test results directory
   * @param resultReport the test results
   * @return true if successful, false if unable to calculate or store the checksum
   */
  public static boolean tryCreateChecksum(File dir, Result resultReport, String buildFingerprint) {
    try {
      int totalCount = countTests(resultReport.getModuleInfoList());
      CompatibilityReportChecksumHelper reportChecksum =
          new CompatibilityReportChecksumHelper(
              totalCount, DEFAULT_FPP, CURRENT_VERSION, buildFingerprint);
      reportChecksum.addResults(resultReport.getModuleInfoList());
      reportChecksum.addDirectory(dir);
      reportChecksum.saveToFile(dir);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  private static int countTests(List<Module> modules) {
    int count = 0;
    for (Module module : modules) {
      count += module.getTotalTests();
    }
    return count;
  }

  private void addResults(List<Module> modules) {
    for (Module module : modules) {
      // First the module result signature
      resultChecksum.put(generateModuleResultSignature(module, buildFingerprint));
      // Second the module summary signature
      resultChecksum.put(generateModuleSummarySignature(module, buildFingerprint));

      for (TestCase testCase : module.getTestCaseList()) {
        for (Test test : testCase.getTestList()) {
          resultChecksum.put(
              buildTestResultSignature(
                  test, module.getName(), testCase.getName(), module.getAbi(), buildFingerprint));
        }
      }
    }
  }

  private static String generateModuleResultSignature(Module module, String buildFingerprint) {
    StringBuilder sb = new StringBuilder();
    sb.append(buildFingerprint)
        .append(SEPARATOR)
        .append(getModuleId(module.getName(), module.getAbi()))
        .append(SEPARATOR)
        .append(module.getDone())
        .append(SEPARATOR)
        .append(countModuleFailTests(module));
    return sb.toString();
  }

  private static String generateModuleSummarySignature(Module module, String buildFingerprint) {
    StringBuilder sb = new StringBuilder();
    sb.append(buildFingerprint)
        .append(SEPARATOR)
        .append(getModuleId(module.getName(), module.getAbi()))
        .append(SEPARATOR)
        .append(countModuleFailTests(module));
    return sb.toString();
  }

  private static int countModuleFailTests(Module module) {
    AtomicInteger count = new AtomicInteger(0);
    module.getTestCaseList().stream()
        .flatMap(testCase -> testCase.getTestList().stream())
        .forEach(
            test -> {
              if (Objects.equals(test.getResult(), "fail")) {
                count.getAndIncrement();
              }
            });
    return count.get();
  }

  /**
   * Creates signature to uniquely identify this test result. Keep in sync with corresponding code
   * in [android] tradefed/result/suite/CertificationChecksumHelper
   */
  private String buildTestResultSignature(
      Test test, String moduleName, String caseName, String abi, String buildFingerprint) {

    String stacktraceStr = "";
    if (test.getFailure().hasStackTrace()) {
      stacktraceStr = test.getFailure().getStackTrace().getContent();
    }

    return generateTestResultSignature(
        moduleName,
        caseName,
        test.getName(),
        test.getResult(),
        stacktraceStr,
        buildFingerprint,
        abi);
  }

  /**
   * Creates signature to uniquely identify this test result. Keep in sync with corresponding code
   * in [android] tradefed/result/suite/CertificationChecksumHelper, format and example:
   *
   * <pre>
   * {fingerprint}/{abi} {suiteName}/{test case name}#{test name}/{status}/{stack trace}/
   *
   * google/bullhead/bullhead:O/MASTER/3402743:userdebug/dev-keys/arm64-v8a CtsBionicTestCases/
   * unistd#lockf_partial_with_child/pass//
   * </pre>
   */
  static String generateTestResultSignature(
      String moduleName,
      String caseName,
      String testName,
      String result,
      @Nullable String stacktrace,
      String buildFingerprint,
      String abi) {
    StringBuilder sb = new StringBuilder();

    stacktrace = stacktrace == null ? "" : stacktrace.trim();
    // Line endings for stacktraces are somewhat unpredictable and there is no need to
    // actually read the result they are all removed for consistency.
    stacktrace = stacktrace.replaceAll("\\r?\\n|\\r", "");
    sb.append(buildFingerprint)
        .append(SEPARATOR)
        .append(getModuleId(moduleName, abi))
        .append(SEPARATOR)
        .append(String.format("%s#%s", caseName, testName))
        .append(SEPARATOR)
        .append(result)
        .append(SEPARATOR)
        .append(stacktrace)
        .append(SEPARATOR);
    return sb.toString();
  }

  /**
   * Builds a unique ID from the module name and abi. Keep in sync with [android] tradefed abiUtils.
   *
   * @return A string represents a unique module ID.
   */
  private static String getModuleId(String moduleName, @Nullable String abi) {
    return String.format("%s %s", abi, moduleName);
  }

  /**
   * Adds all child files recursively through all sub directories.
   *
   * @param directory target that is deeply searched for files
   */
  private void addDirectory(File directory) {
    addDirectory(directory, directory.getName());
  }

  /**
   * @param path the relative path to the current directory from the base directory
   */
  private void addDirectory(File directory, String path) {
    for (String childName : directory.list()) {
      File child = new File(directory, childName);
      if (child.isDirectory()) {
        addDirectory(child, path + SEPARATOR + child.getName());
      } else {
        addFile(child, path);
      }
    }
  }

  /**
   * Calculates CRC of file and store the result.
   *
   * @param file crc calculated on this file
   * @param path part of the key to identify the file crc
   */
  private void addFile(File file, String path) {
    byte[] crc;
    try {
      crc = calculateFileChecksum(file);
    } catch (ChecksumValidationException e) {
      crc = new byte[0];
    }
    String key = path + SEPARATOR + file.getName();
    fileChecksum.put(key, crc);
  }

  /**
   * Creates Sha-256 hash of file content, keep in sync with corresponding code in [android]
   * tradefed/result/suite/CertificationChecksumHelper
   */
  private static byte[] calculateFileChecksum(File file) throws ChecksumValidationException {
    try (FileInputStream fis = new FileInputStream(file);
        InputStream inputStream = new BufferedInputStream(fis)) {
      MessageDigest hashSum = MessageDigest.getInstance("SHA-256");
      int cnt;
      int bufferSize = 8192;
      byte[] buffer = new byte[bufferSize];
      while ((cnt = inputStream.read(buffer)) != -1) {
        hashSum.update(buffer, 0, cnt);
      }

      byte[] partialHash = new byte[32];
      hashSum.digest(partialHash, 0, 32);
      return partialHash;
    } catch (NoSuchAlgorithmException | IOException | DigestException e) {
      throw new ChecksumValidationException("Unable to hash file.", e);
    }
  }

  /**
   * Writes the checksum data to disk.
   *
   * <p>Overwrites existing file.
   *
   * @param directory where to store the checksum file
   * @throws IOException if failed to write the checksum data to disk
   */
  private void saveToFile(File directory) throws IOException {
    File file = new File(directory, NAME);

    try (FileOutputStream fileStream = new FileOutputStream(file, false);
        OutputStream outputStream = new BufferedOutputStream(fileStream);
        ObjectOutput objectOutput = new ObjectOutputStream(outputStream)) {
      objectOutput.writeShort(SERIALIZED_FORMAT_CODE);
      objectOutput.writeShort(version);
      objectOutput.writeObject(resultChecksum);
      objectOutput.writeObject(fileChecksum);
    }
  }

  /** Uses this method to test that a test entry can be matched by the checksum. */
  @VisibleForTesting
  boolean containsTestResult(Module module, TestCase testCase, Test test, String buildFingerprint) {
    String signature =
        buildTestResultSignature(
            test, module.getName(), testCase.getName(), module.getAbi(), buildFingerprint);
    return resultChecksum.mightContain(signature);
  }

  /** Uses this method to validate that a file entry can be checked by the checksum. */
  @VisibleForTesting
  boolean containsFile(File file, String path) {
    String key = path + SEPARATOR + file.getName();
    if (fileChecksum.containsKey(key)) {
      try {
        byte[] crc = calculateFileChecksum(file);
        return Arrays.equals(fileChecksum.get(key), crc);
      } catch (ChecksumValidationException e) {
        return false;
      }
    }
    return false;
  }

  /** Explicit exception for report checksum validation. */
  public static class ChecksumValidationException extends Exception {
    public ChecksumValidationException(String detailMessage) {
      super(detailMessage);
    }

    public ChecksumValidationException(String detailMessage, Throwable throwable) {
      super(detailMessage, throwable);
    }
  }
}
