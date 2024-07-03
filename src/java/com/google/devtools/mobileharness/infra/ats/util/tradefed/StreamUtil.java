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

import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;

/** Utility class for managing input streams. */
public class StreamUtil {

  // 16K buffer size
  private static final int BUF_SIZE = 16 * 1024;

  private StreamUtil() {}

  /**
   * Retrieves a {@link String} from a character stream.
   *
   * @param stream the {@link InputStream}
   * @return a {@link String} containing the stream contents
   * @throws MobileHarnessException if failure occurred reading the stream
   */
  public static String getStringFromStream(InputStream stream) throws MobileHarnessException {
    return getStringFromStream(stream, 0);
  }

  /**
   * Retrieves a {@link String} from a character stream.
   *
   * @param stream the {@link InputStream}
   * @param length the size of the content to read, set to 0 to read all contents
   * @return a {@link String} containing the stream contents
   * @throws MobileHarnessException if failure occurred reading the stream
   */
  public static String getStringFromStream(InputStream stream, long length)
      throws MobileHarnessException {
    int irChar = -1;
    StringBuilder builder = new StringBuilder();
    try (Reader ir = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      long count = 0;
      while ((irChar = ir.read()) != -1) {
        builder.append((char) irChar);
        count++;
        if (length > 0 && count >= length) {
          break;
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_GET_STRING_FROM_STREAM_ERROR,
          "Failed to get string from input stream",
          e);
    }
    return builder.toString();
  }

  /**
   * Retrieves a {@link ByteArrayList} from a byte stream.
   *
   * @param stream the {@link InputStream}
   * @return a {@link ByteArrayList} containing the stream contents
   * @throws MobileHarnessException if failure occurred reading the stream
   */
  public static ByteArrayList getByteArrayListFromStream(InputStream stream)
      throws MobileHarnessException {
    InputStream is = new BufferedInputStream(stream);
    int inputByte = -1;
    ByteArrayList list = new ByteArrayList();
    try {
      while ((inputByte = is.read()) != -1) {
        list.add((byte) inputByte);
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_GET_BYTE_ARRAY_LIST_FROM_STREAM_ERROR,
          "Failed to get ByteArrayList from input stream",
          e);
    } finally {
      StreamUtil.close(is);
    }
    list.trimToSize();
    return list;
  }

  /**
   * Copies contents of origStream to destStream.
   *
   * <p>Recommended to provide a buffered stream for input and output
   *
   * @param inStream the {@link InputStream}
   * @param outStream the {@link OutputStream}
   * @throws MobileHarnessException if failed to copy contents of original stream to destination
   *     stream
   */
  public static void copyStreams(InputStream inStream, OutputStream outStream)
      throws MobileHarnessException {
    copyStreams(inStream, outStream, 0);
  }

  /**
   * Copies contents of origStream to destStream.
   *
   * <p>Recommended to provide a buffered stream for input and output
   *
   * @param inStream the {@link InputStream}
   * @param outStream the {@link OutputStream}
   * @param offset the offset of when to start copying the data.
   * @throws MobileHarnessException if failed to copy contents of original stream to destination
   *     stream
   */
  public static void copyStreams(InputStream inStream, OutputStream outStream, int offset)
      throws MobileHarnessException {
    // Set size to a negative value to copy all content starting at the given offset.
    copyStreams(inStream, outStream, offset, -1);
  }

  /**
   * Copies contents of origStream to destStream starting at a given offset with a specific size.
   *
   * <p>Recommended to provide a buffered stream for input and output
   *
   * @param inStream the {@link InputStream}
   * @param outStream the {@link OutputStream}
   * @param offset the offset of when to start copying the data.
   * @param size the number of bytes to copy. A negative value means to copy all content.
   * @throws MobileHarnessException if failed to copy contents of original stream to destination
   *     stream
   */
  public static void copyStreams(
      InputStream inStream, OutputStream outStream, long offset, long size)
      throws MobileHarnessException {
    Preconditions.checkArgument(offset >= 0, "offset must be greater or equal to zero.");
    Preconditions.checkArgument(size != 0, "size cannot be zero.");
    try {
      inStream.skip(offset);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_SKIP_BYTES_ERROR,
          String.format("Failed to skip %d bytes in the input stream.", offset),
          e);
    }
    byte[] buf = new byte[BUF_SIZE];
    long totalRetrievedSize = 0;
    try {
      while (true) {
        int maxReadSize = size > 0 ? (int) min(size - totalRetrievedSize, buf.length) : buf.length;
        int retrievedSize = inStream.read(buf, 0, maxReadSize);
        if (retrievedSize == -1) {
          break;
        }
        outStream.write(buf, 0, retrievedSize);
        totalRetrievedSize += retrievedSize;
        if (size == totalRetrievedSize) {
          break;
        }
      }
    } catch (IOException e) {
      if (e.getMessage().contains("No space left on device")) {
        throw new MobileHarnessException(
            BasicErrorId.STREAM_UTIL_NO_DISK_SPACE,
            "No space left on device when copying streams.",
            e);
      }
    }
    if (size > 0 && size > totalRetrievedSize) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_COPY_STREAMS_ERROR,
          String.format(
              "Failed to read %d bytes starting at offset %d, only %d bytes retrieved.",
              size, offset, totalRetrievedSize));
    }
  }

  /**
   * Copies contents of inStream to writer.
   *
   * <p>Recommended to provide a buffered stream for input and output
   *
   * @param inStream the {@link InputStream}
   * @param writer the {@link Writer} destination
   * @throws MobileHarnessException if failed to copy contents of a stream to writer
   */
  public static void copyStreamToWriter(InputStream inStream, Writer writer)
      throws MobileHarnessException {
    byte[] buf = new byte[BUF_SIZE];
    int size = -1;
    try {
      while ((size = inStream.read(buf)) != -1) {
        writer.write(new String(buf, 0, size, UTF_8));
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_COPY_STREAM_TO_WRITER_ERROR,
          "Failed to copy stream to writer.",
          e);
    }
  }

  /**
   * Copies contents of file to outStream. It is recommended to provide a buffered stream.
   *
   * @param file the {@link File}
   * @param outStream the {@link OutputStream}
   * @throws MobileHarnessException if failed to copy contents of a file to the output stream
   */
  public static void copyFileToStream(File file, OutputStream outStream)
      throws MobileHarnessException {
    InputStream inStream = null;
    try {
      inStream = new FileInputStream(file);
      inStream = new BufferedInputStream(inStream);
      StreamUtil.copyStreams(inStream, outStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_COPY_FILE_TO_STREAM_ERROR,
          String.format("Failed to copy file %s to stream.", file.getAbsolutePath()),
          e);
    } finally {
      StreamUtil.close(inStream);
    }
  }

  /**
   * Gets the stack trace as a {@link String}.
   *
   * @param throwable the {@link Throwable} to convert.
   * @return a {@link String} stack trace
   */
  public static String getStackTrace(Throwable throwable) {
    // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream bytePrintStream = new PrintStream(outputStream);
    throwable.printStackTrace(bytePrintStream);
    return outputStream.toString(UTF_8);
  }

  /**
   * Attempts to flush the given output stream, and then closes it.
   *
   * @param outStream the {@link OutputStream}. No action taken if outStream is null.
   */
  public static void flushAndCloseStream(OutputStream outStream) {
    if (outStream != null) {
      try {
        outStream.flush();
      } catch (IOException e) {
        // ignore
      }
      try {
        outStream.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Closes given zip output stream.
   *
   * @param outStream the {@link ZipOutputStream}. No action taken if outStream is null.
   */
  public static void closeZipStream(ZipOutputStream outStream) {
    if (outStream != null) {
      try {
        outStream.closeEntry();
        outStream.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Closes given gzip output stream.
   *
   * @param outStream the {@link ZipOutputStream}. No action taken if outStream is null.
   */
  public static void closeGZipStream(GZIPOutputStream outStream) {
    if (outStream != null) {
      try {
        outStream.finish();
        outStream.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Closes the given {@link Closeable}.
   *
   * @param closeable the {@link Closeable}. No action taken if <code>null</code>.
   */
  public static void close(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /** Create a {@link OutputStream} that discards all writes. */
  public static OutputStream nullOutputStream() {
    return ByteStreams.nullOutputStream();
  }

  /**
   * Helper method to calculate CRC-32 for an {@link InputStream}. The stream will be consumed and
   * closed. It is recommended to provide a buffered stream.
   *
   * @param inStream the {@link InputStream}
   * @return CRC-32 of the stream
   * @throws MobileHarnessException if failed to calculate CRC-32 for the input stream
   */
  public static long calculateCrc32(InputStream inStream) throws MobileHarnessException {
    CRC32 crc32 = new CRC32();
    byte[] buf = new byte[BUF_SIZE];
    int size = -1;
    try (inStream) {
      while ((size = inStream.read(buf)) >= 0) {
        crc32.update(buf, 0, size);
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_CALCULATE_CRC32_ERROR, "Failed to calculate CRC32.", e);
    }
    return crc32.getValue();
  }

  /**
   * Helper method to calculate md5 for a inputStream. The inputStream will be consumed and closed.
   *
   * @param inputSource used to create inputStream
   * @return md5 of the stream
   * @throws MobileHarnessException if failed to calculate md5 for the input stream
   */
  public static String calculateMd5(InputStream inputSource) throws MobileHarnessException {
    return bytesToHexString(calculateMd5Digest(inputSource));
  }

  /**
   * Helper method to calculate base64 md5 for a inputStream. The inputStream will be consumed and
   * closed.
   *
   * @param inputSource used to create inputStream
   * @return base64 md5 of the stream
   * @throws MobileHarnessException if failed to calculate base64 md5 for the input stream
   */
  public static String calculateBase64Md5(InputStream inputSource) throws MobileHarnessException {
    return Base64.getEncoder().encodeToString(calculateMd5Digest(inputSource));
  }

  private static byte[] calculateMd5Digest(InputStream inputSource) throws MobileHarnessException {
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("md5");
    } catch (NoSuchAlgorithmException e) {
      // This should not happen
      throw new IllegalStateException(e);
    }
    InputStream input = new BufferedInputStream(new DigestInputStream(inputSource, md));
    byte[] buf = new byte[BUF_SIZE];
    try {
      while (input.read(buf) != -1) {
        // Read through the stream to update digest.
      }
      input.close();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.STREAM_UTIL_CALCULATE_MD5_DIGEST_ERROR,
          "Failed to calculate md5 digest for the input stream.",
          e);
    }
    return md.digest();
  }

  private static final char[] HEX_CHARS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  /**
   * Converts a byte array into a String of hexadecimal characters.
   *
   * @param bytes an array of bytes
   * @return hex string representation of bytes array
   */
  private static String bytesToHexString(byte[] bytes) {
    Objects.requireNonNull(bytes);
    StringBuilder sb = new StringBuilder(2 * bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      int b = 0x0f & (bytes[i] >> 4);
      sb.append(HEX_CHARS[b]);
      b = 0x0f & bytes[i];
      sb.append(HEX_CHARS[b]);
    }
    return sb.toString();
  }
}
