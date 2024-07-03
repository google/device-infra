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

package com.google.devtools.mobileharness.infra.ats.util.tradefed;

/** Represents the data type of log data. */
public enum LogDataType {
  TEXT("txt", "text/plain", false, true),
  UIX("uix", "text/xml", false, true),
  XML("xml", "text/xml", false, true),
  HTML("html", "text/html", true, true),
  PNG("png", "image/png", true, false),
  MP4("mp4", "video/mp4", true, false),
  EAR("ear", "application/octet-stream", true, false),
  ZIP("zip", "application/zip", true, false),
  SEVEN_Z("7z", "application/x-7z-compressed", true, false),
  BITS("bits", "application/octet-stream", true, false),
  JPEG("jpeg", "image/jpeg", true, false),
  TAR_GZ("tar.gz", "application/gzip", true, false),
  GZIP("gz", "application/gzip", true, false),
  HPROF("hprof", "application/octet-stream", true, false),
  COVERAGE("ec", "text/plain", true /* do not compress */, false), // Emma coverage file
  NATIVE_COVERAGE("zip", "application/zip", true, false), // gcov coverage archive
  CLANG_COVERAGE(
      "profdata", "text/plain", true /* do not compress */, false), // LLVM indexed profile data
  GCOV_KERNEL_COVERAGE(
      "tar.gz",
      "application/gzip",
      true /* do not compress */,
      false), // GCOV debugfs coverage archive
  PB("pb", "application/octet-stream", true, false), // Binary proto file
  TEXTPB("textproto", "text/plain", false, true), // Text proto file
  JSON("json", "application/json", false, true),
  PERFETTO(
      "perfetto-trace",
      "application/octet-stream",
      false, // Not compressed by default, so we can gzip them
      false), // binary proto perfetto trace file
  TRACE(
      "trace",
      "application/octet-stream",
      false, // Not compressed by default, so we can gzip them
      false), // binary method trace file
  /* Specific text file types */
  ANRS("txt", "text/plain", true, true),
  BUGREPORT("txt", "text/plain", false, true),
  BUGREPORTZ("zip", "application/zip", true, false),
  HOST_LOG("txt", "text/plain", true, true),
  LOGCAT("txt", "text/plain", true, true),
  KERNEL_LOG("txt", "text/plain", true, true),
  MONKEY_LOG("txt", "text/plain", false, true),
  MUGSHOT_LOG("txt", "text/plain", false, true),
  CB_METRICS_FILE("txt", "text/plain", true /* TODO: Allow compression when supported */, true),
  PROCRANK("txt", "text/plain", false, true),
  MEM_INFO("txt", "text/plain", false, true),
  TOP("txt", "text/plain", false, true),
  DUMPSYS("txt", "text/plain", false, true),
  DUMPTRACE("txt", "text/plain", true, true),
  COMPACT_MEMINFO("txt", "text/plain", false, true), // dumpsys meminfo -c
  SERVICES("txt", "text/plain", false, true), // dumpsys activity services
  GFX_INFO("txt", "text/plain", false, true), // dumpsys gfxinfo
  CPU_INFO("txt", "text/plain", false, true), // dumpsys cpuinfo
  JACOCO_CSV("csv", "text/csv", false, true), // JaCoCo coverage report in CSV format
  JACOCO_XML("xml", "text/xml", false, true), // JaCoCo coverage report in XML format
  JACOCO_EXEC("exec", "application/octet-stream", false, false), // JaCoCo coverage execution file
  ATRACE("atr", "text/plain", true, false), // atrace -z format
  KERNEL_TRACE("dat", "text/plain", false, false), // raw kernel ftrace buffer
  DIR("", "text/plain", false, false),
  CFG("cfg", "application/octet-stream", false, true),
  TF_EVENTS("txt", "text/plain", true, true),
  HARNESS_STD_LOG("txt", "text/plain", true, true),
  HARNESS_CONFIG("xml", "text/xml", true, true),
  ADB_HOST_LOG("txt", "text/plain", true, true),
  PASSED_TESTS("txt", "text/plain", true, true),
  RECOVERY_MODE_LOG("txt", "text/plain", false, true),
  GOLDEN_RESULT_PROTO(
      "textproto",
      "text/plain",
      true, // b/230070438: don't compress this file
      true), // ScreenshotTest proto result
  CUTTLEFISH_LOG("txt", "text/plain", true, true), // Log from cuttlefish instance
  TOMBSTONEZ("zip", "application/zip", true, false),
  BT_SNOOP_LOG("log", "application/octet-stream", false, false), // Bluetooth HCI snoop logs
  /* Unknown file type */
  UNKNOWN("dat", "text/plain", false, false);

  private final String mFileExt; // Usual extension of the file type
  private final String mContentType;
  // If the type is already compressed or should never be compressed
  private final boolean mIsCompressedOrNeverCompress;
  private final boolean mIsText;

  LogDataType(
      String fileExt, String contentType, boolean isCompressedOrNeverCompress, boolean text) {
    mFileExt = fileExt;
    mIsCompressedOrNeverCompress = isCompressedOrNeverCompress;
    mIsText = text;
    mContentType = contentType;
  }

  public String getFileExt() {
    return mFileExt;
  }

  public String getContentType() {
    return mContentType;
  }

  /**
   * @return <code>true</code> if data type is a compressed format or should not be compressed.
   */
  public boolean isCompressed() {
    return mIsCompressedOrNeverCompress;
  }

  /**
   * @return <code>true</code> if data type is a textual format.
   */
  public boolean isText() {
    return mIsText;
  }
}
