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
import java.util.Arrays;

/**
 * CentralDirectoryInfo is a class containing the information of a file/folder inside a zip file.
 *
 * <p>Overall zipfile format: [Local file header + Compressed data [+ Extended local header]?]*
 * [Central directory]* [End of central directory record]
 *
 * <p>Refer to following link for more details: https://en.wikipedia.org/wiki/Zip_(file_format)
 */
public final class CentralDirectoryInfo {

  private static final byte[] CENTRAL_DIRECTORY_SIGNATURE = {0x50, 0x4b, 0x01, 0x02};
  private static final int ZIP64_EXTRA_FIELD_HEADER_ID = 0x0001;

  private int compressionMethod;
  private long crc;
  private long compressedSize;
  private long uncompressedSize;
  private long localHeaderOffset;
  private int internalFileAttributes;
  private long externalFileAttributes;
  private String fileName;
  private int fileNameLength;
  private int extraFieldLength;
  private int fileCommentLength;

  /** Get the compression method. */
  public int getCompressionMethod() {
    return compressionMethod;
  }

  /** Set the compression method. */
  public void setCompressionMethod(int compressionMethod) {
    this.compressionMethod = compressionMethod;
  }

  /** Get the CRC of the file. */
  public long getCrc() {
    return crc;
  }

  /** Set the CRC of the file. */
  public void setCrc(long crc) {
    this.crc = crc;
  }

  /** Get the compressed size. */
  public int getCompressedSize() {
    return (int) compressedSize;
  }

  /** Set the compressed size. */
  public void setCompressedSize(long compressionSize) {
    compressedSize = compressionSize;
  }

  /** Get the uncompressed size. */
  public long getUncompressedSize() {
    return uncompressedSize;
  }

  /** Checks if this is a symlink. */
  public boolean isSymLink() {
    return ((externalFileAttributes >> 16L) & 0XA000) == 0XA000;
  }

  /** Set the uncompressed size. */
  public void setUncompressedSize(long uncompressedSize) {
    this.uncompressedSize = uncompressedSize;
  }

  /** Get the offset of local file header entry. */
  public long getLocalHeaderOffset() {
    return localHeaderOffset;
  }

  /** Set the offset of local file header entry. */
  public void setLocalHeaderOffset(long localHeaderOffset) {
    this.localHeaderOffset = localHeaderOffset;
  }

  /** Get the internal file attributes. */
  public int getInternalFileAttributes() {
    return internalFileAttributes;
  }

  /** Set the internal file attributes. */
  public void setInternalFileAttributes(int internalFileAttributes) {
    this.internalFileAttributes = internalFileAttributes;
  }

  /** Get the external file attributes. */
  public long getExternalFileAttributes() {
    return externalFileAttributes;
  }

  /** Set the external file attributes. */
  public void setExternalFileAttributes(long externalFileAttributes) {
    this.externalFileAttributes = externalFileAttributes;
  }

  /** Get the Linux file permission, stored in the last 9 bits of external file attributes. */
  public int getFilePermission() {
    return ((int) externalFileAttributes & (0777 << 16L)) >> 16L;
  }

  /** Get the file name including the relative path. */
  public String getFileName() {
    return fileName;
  }

  /** Set the file name including the relative path. */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /** Get the file name length. */
  public int getFileNameLength() {
    return fileNameLength;
  }

  /** Set the file name length. */
  public void setFileNameLength(int fileNameLength) {
    this.fileNameLength = fileNameLength;
  }

  /** Get the extra field length. */
  public int getExtraFieldLength() {
    return extraFieldLength;
  }

  /** Set the extra field length. */
  public void setExtraFieldLength(int extraFieldLength) {
    this.extraFieldLength = extraFieldLength;
  }

  /** Get the file comment length. */
  public int getFileCommentLength() {
    return fileCommentLength;
  }

  /** Set the file comment length. */
  public void setFileCommentLength(int fileCommentLength) {
    this.fileCommentLength = fileCommentLength;
  }

  /** Get the size of the central directory entry. */
  public int getInfoSize() {
    return 46 + fileNameLength + extraFieldLength + fileCommentLength;
  }

  /**
   * Constructor to collect the information of a file entry inside zip file.
   *
   * @param data {@code byte[]} of data that contains the information of a file entry
   * @param startOffset start offset of the information block
   * @throws MobileHarnessException if failed to create the central directory info
   */
  public CentralDirectoryInfo(byte[] data, int startOffset) throws MobileHarnessException {
    this(data, startOffset, false);
  }

  /**
   * Constructor to collect the information of a file entry inside zip file.
   *
   * @param data {@code byte[]} of data that contains the information of a file entry
   * @param startOffset start offset of the information block
   * @param useZip64 a boolean to support zip64 format in partial download
   * @throws MobileHarnessException if failed to create the central directory info
   */
  public CentralDirectoryInfo(byte[] data, int startOffset, boolean useZip64)
      throws MobileHarnessException {
    // Central directory:
    //    Offset   Length   Contents
    //      0      4 bytes  Central file header signature (0x02014b50)
    //      4      2 bytes  Version made by
    //      6      2 bytes  Version needed to extract
    //      8      2 bytes  General purpose bit flag
    //     10      2 bytes  Compression method
    //     12      2 bytes  Last mod file time
    //     14      2 bytes  Last mod file date
    //     16      4 bytes  CRC-32
    //     20      4 bytes  Compressed size
    //     24      4 bytes  Uncompressed size
    //     28      2 bytes  Filename length (f)
    //     30      2 bytes  Extra field length (e)
    //     32      2 bytes  File comment length (c)
    //     34      2 bytes  Disk number start
    //     36      2 bytes  Internal file attributes
    //     38      4 bytes  External file attributes (file permission stored in the last 9 bits)
    //     42      4 bytes  Relative offset of local header
    //     46     (f)bytes  Filename
    //            (e)bytes  Extra field
    //            (c)bytes  File comment

    // Check signature
    if (!Arrays.equals(
        CENTRAL_DIRECTORY_SIGNATURE, Arrays.copyOfRange(data, startOffset, startOffset + 4))) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_INVALID_CENTRAL_DIRECTORY_INFO,
          "Invalid central directory info for zip file is found.");
    }
    compressionMethod = ByteArrayUtil.getInt(data, startOffset + 10, 2);
    crc = ByteArrayUtil.getLong(data, startOffset + 16, 4);
    compressedSize = ByteArrayUtil.getLong(data, startOffset + 20, 4);
    uncompressedSize = ByteArrayUtil.getLong(data, startOffset + 24, 4);
    internalFileAttributes = ByteArrayUtil.getInt(data, startOffset + 36, 2);
    externalFileAttributes = ByteArrayUtil.getLong(data, startOffset + 38, 4);
    localHeaderOffset = ByteArrayUtil.getLong(data, startOffset + 42, 4);
    fileNameLength = ByteArrayUtil.getInt(data, startOffset + 28, 2);
    fileName = ByteArrayUtil.getString(data, startOffset + 46, fileNameLength);
    extraFieldLength = ByteArrayUtil.getInt(data, startOffset + 30, 2);
    fileCommentLength = ByteArrayUtil.getInt(data, startOffset + 32, 2);
    if (!useZip64) {
      return;
    }
    // Get the real data while use-zip64-in-partial-download is set and the 3 corresponding
    // elements match the condition.
    if (Long.toHexString(uncompressedSize).equals("ffffffff")
        || Long.toHexString(compressedSize).equals("ffffffff")
        || Long.toHexString(localHeaderOffset).equals("ffffffff")) {

      // Read through extra field. The extra field consist of
      //   header1+data1 + header2+data2 + header3+data3 . . .
      // Each header contains 2 bytes of Header ID and 2 bytes of Data Size.
      boolean hasZip64HeaderId = false;
      int currPos = startOffset + fileNameLength + 46;
      int endPos = currPos + extraFieldLength;
      while (currPos + 4 <= endPos) {
        int headerId = ByteArrayUtil.getInt(data, currPos, 2);
        int size = ByteArrayUtil.getInt(data, currPos + 2, 2);
        if (headerId == ZIP64_EXTRA_FIELD_HEADER_ID) {
          uncompressedSize = ByteArrayUtil.getLong(data, currPos + 4, 8);
          compressedSize = ByteArrayUtil.getLong(data, currPos + 12, 8);
          localHeaderOffset = ByteArrayUtil.getLong(data, currPos + 20, 8);
          hasZip64HeaderId = true;
          break;
        }
        currPos += 4 + size;
      }
      // There should be a ZIP64 Field ID(0x0001) existing here.
      if (!hasZip64HeaderId) {
        throw new IllegalStateException(
            String.format(
                "Failed to find ZIP64 field id(0x0001) from the Central Directory Info for file:"
                    + " %s",
                fileName));
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    return this.toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public String toString() {
    return String.format(
        "Compression Method: %d\n"
            + "Crc: %d\n"
            + "Compressed Size: %d\n"
            + "Uncompressed Size: %d\n"
            + "Local Header Offset: %d\n"
            + "Internal File Attributes: %d\n"
            + "External File Attributes: %d\n"
            + "File Name: %s\n"
            + "File Name Length: %d\n"
            + "Extra Field Length: %d\n"
            + "File Comment Length: %d",
        compressionMethod,
        crc,
        compressedSize,
        uncompressedSize,
        localHeaderOffset,
        internalFileAttributes,
        externalFileAttributes,
        fileName,
        fileNameLength,
        extraFieldLength,
        fileCommentLength);
  }
}
