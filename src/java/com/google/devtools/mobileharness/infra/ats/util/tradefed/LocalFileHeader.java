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

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * LocalFileHeader is a class containing the information of a file/folder inside a zip file. The
 * block of data is at the beginning part of each file entry.
 *
 * <p>Overall zipfile format: [Local file header + Compressed data [+ Extended local header]?]*
 * [Central directory]* [End of central directory record]
 *
 * <p>Refer to following link for more details: https://en.wikipedia.org/wiki/Zip_(file_format)
 */
public final class LocalFileHeader {

  public static final int LOCAL_FILE_HEADER_SIZE = 30;
  private static final byte[] LOCAL_FILE_HEADER_SIGNATURE = {0x50, 0x4b, 0x03, 0x04};

  private final int compressionMethod;
  private final long crc;
  private final long compressedSize;
  private final long uncompressedSize;
  private final int fileNameLength;
  private final int extraFieldLength;

  public int getCompressionMethod() {
    return compressionMethod;
  }

  public long getCrc() {
    return crc;
  }

  public long getCompressedSize() {
    return compressedSize;
  }

  public long getUncompressedSize() {
    return uncompressedSize;
  }

  public int getFileNameLength() {
    return fileNameLength;
  }

  public int getExtraFieldLength() {
    return extraFieldLength;
  }

  public int getHeaderSize() {
    return LOCAL_FILE_HEADER_SIZE + fileNameLength + extraFieldLength;
  }

  public LocalFileHeader(File partialZipFile) throws MobileHarnessException {
    this(partialZipFile, 0);
  }

  /**
   * Constructor to collect local file header information of a file entry in a zip file.
   *
   * @param partialZipFile a {@link File} contains the local file header information.
   * @param startOffset the start offset of the block of data for a local file header.
   * @throws MobileHarnessException if failed to read the local file header.
   */
  public LocalFileHeader(File partialZipFile, long startOffset) throws MobileHarnessException {
    // Local file header:
    //    Offset   Length   Contents
    //      0      4 bytes  Local file header signature (0x04034b50)
    //      4      2 bytes  Version needed to extract
    //      6      2 bytes  General purpose bit flag
    //      8      2 bytes  Compression method
    //     10      2 bytes  Last mod file time
    //     12      2 bytes  Last mod file date
    //     14      4 bytes  CRC-32
    //     18      4 bytes  Compressed size (n)
    //     22      4 bytes  Uncompressed size
    //     26      2 bytes  Filename length (f)
    //     28      2 bytes  Extra field length (e)
    //            (f)bytes  Filename
    //            (e)bytes  Extra field
    //            (n)bytes  Compressed data
    byte[] data;
    try (FileInputStream stream = new FileInputStream(partialZipFile)) {
      stream.skip(startOffset);
      data = new byte[LOCAL_FILE_HEADER_SIZE];
      stream.read(data);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR,
          String.format(
              "Failed to read local file header from zip file: %s",
              partialZipFile.getAbsolutePath()),
          e);
    }

    // Check signature
    if (!Arrays.equals(LOCAL_FILE_HEADER_SIGNATURE, Arrays.copyOf(data, 4))) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_INVALID_LOCAL_FILE_HEADER_ERROR,
          String.format(
              "Invalid local file header for zip file is found: %s",
              partialZipFile.getAbsolutePath()));
    }
    compressionMethod = ByteArrayUtil.getInt(data, 8, 2);
    crc = ByteArrayUtil.getLong(data, 14, 4);
    compressedSize = ByteArrayUtil.getLong(data, 18, 2);
    uncompressedSize = ByteArrayUtil.getLong(data, 22, 2);
    fileNameLength = ByteArrayUtil.getInt(data, 26, 2);
    extraFieldLength = ByteArrayUtil.getInt(data, 28, 2);
  }
}
