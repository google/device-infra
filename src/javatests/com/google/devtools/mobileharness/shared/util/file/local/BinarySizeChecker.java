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

package com.google.devtools.mobileharness.shared.util.file.local;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.Comparator.comparingLong;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** Checker for checking binary sizes. */
public class BinarySizeChecker {

  private static final double NEARLY_LARGE_FACTOR = 0.9;

  public static void checkBinarySize(
      String binaryName, long maxSizeByte, String binaryFilePath, String binarySourcePath)
      throws MobileHarnessException {
    assertWithMessage(
            "The binary size of %s should be less than %s bytes. If you are sure that the new added"
                + " deps are necessary, please update the number and explain the necessity (what"
                + " libs are added to the binary, their sizes, why they are necessary) in the"
                + " change description.\n\nbinary_path=%s\n\nBinary size of %s:",
            binaryName, maxSizeByte, binarySourcePath, binaryName)
        .that(new LocalFileUtil().getFileSize(binaryFilePath))
        .isLessThan(maxSizeByte);
  }

  public static void checkBinaryLargeResourceFiles(
      String binaryName,
      String binaryFilePath,
      long maxResourceFileSizeByte,
      ImmutableSet<String> largeResourcePathAllowlist,
      String binarySourcePath,
      String allowlistSourcePath)
      throws IOException {

    long nearlyMaxResourceFileSizeByte = (long) (maxResourceFileSizeByte * NEARLY_LARGE_FACTOR);

    ImmutableMap<String, Long> largeResourcesPathAndSize;
    ImmutableMap<String, Long> nearlyLargeResourcesPathAndSize;
    try (JarFile jarFile = new JarFile(binaryFilePath)) {
      largeResourcesPathAndSize =
          jarFile.stream()
              .filter(jarEntry -> jarEntry.getSize() > maxResourceFileSizeByte)
              .sorted(comparingLong(ZipEntry::getSize).reversed())
              .collect(toImmutableMap(ZipEntry::getName, ZipEntry::getSize));
      nearlyLargeResourcesPathAndSize =
          jarFile.stream()
              .filter(jarEntry -> jarEntry.getSize() > nearlyMaxResourceFileSizeByte)
              .sorted(comparingLong(ZipEntry::getSize).reversed())
              .collect(toImmutableMap(ZipEntry::getName, ZipEntry::getSize));
    }

    assertWithMessage(
            "All large resource files in %s must be added to an allowlist. If you believe adding a"
                + " new large resource file or increasing the size of an existing one is essential,"
                + " please add the resource path to the allowlist and clearly document the"
                + " necessity for this inclusion in the cl description.\n\n"
                + "large_resource_file_allowlist=%s\n"
                + "resource_file_size_limit=%s(bytes)\n"
                + "binary_path=%s\n\n"
                + "Large resource files in %s but not in the allowlist:",
            binaryName, allowlistSourcePath, maxResourceFileSizeByte, binarySourcePath, binaryName)
        .that(
            largeResourcesPathAndSize.entrySet().stream()
                .filter(e -> !largeResourcePathAllowlist.contains(e.getKey()))
                .collect(toImmutableMap(Entry::getKey, Entry::getValue)))
        .isEmpty();

    assertWithMessage(
            "Please update %s large resource file allowlist to remove items that are not large"
                + " resources in %s.\n\n"
                + "large_resource_file_allowlist=%s\n"
                + "resource_file_size_limit * %s=%s(bytes)\n"
                + "binary_path=%s\n\n"
                + "Allowlist items that are not large resource files in %s:",
            binaryName,
            binaryName,
            allowlistSourcePath,
            NEARLY_LARGE_FACTOR,
            nearlyMaxResourceFileSizeByte,
            binarySourcePath,
            binaryName)
        .that(
            largeResourcePathAllowlist.stream()
                .filter(path -> !nearlyLargeResourcesPathAndSize.containsKey(path))
                .collect(toImmutableList()))
        .isEmpty();
  }

  private BinarySizeChecker() {}
}
