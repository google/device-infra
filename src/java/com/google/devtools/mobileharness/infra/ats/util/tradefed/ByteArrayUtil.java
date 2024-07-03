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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Utilities to operate on byte array, e.g., convert bytes to integer.
 *
 * <p>Java doesn't have an unsigned value type, so expansion is needed to convert an unsigned
 * integer stored in 4 bytes to a long value, or unsigned short stored in 2 bytes to an integer
 * value.
 */
public class ByteArrayUtil {

  /**
   * Get a {@link ByteBuffer} for the given bytes wrapped in a byte array of given size.
   *
   * <p>java doesn't have an unsigned value type, so expansion is needed to convert an unsigned
   * short stored in 2 bytes to an integer value.
   *
   * @param bytes an array of bytes.
   * @param offset the start offset of the integer data.
   * @param length the length of the integer data.
   * @param containerSize the size of the array to store the given bytes, append zero to unfilled
   *     items.
   * @return a {@link ByteBuffer} for the given bytes wrapped in a byte array of given size.
   */
  private static ByteBuffer getByteBuffer(byte[] bytes, int offset, int length, int containerSize) {
    byte[] data = new byte[containerSize];
    for (int i = 0; i < length; i++) {
      data[i] = bytes[offset + i];
    }
    return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Get an integer from the given bytes.
   *
   * <p>java doesn't have an unsigned value type, so expansion is needed to convert an unsigned
   * short stored in 2 bytes to an integer value.
   *
   * @param bytes an array of bytes.
   * @param offset the start offset of the integer data.
   * @param length the length of the integer data.
   * @return an int value from the given bytes.
   */
  public static int getInt(byte[] bytes, int offset, int length) {
    return getByteBuffer(bytes, offset, length, 4).getInt();
  }

  /**
   * Get a long value from the given bytes.
   *
   * <p>java doesn't have an unsigned value type, so expansion is needed to convert an unsigned
   * integer stored in 4 bytes to a long value.
   *
   * @param bytes an array of bytes.
   * @param offset the start offset of the long value.
   * @param length the length of the long value.
   * @return a long value from the given bytes.
   */
  public static long getLong(byte[] bytes, int offset, int length) {
    return getByteBuffer(bytes, offset, length, 8).getLong();
  }

  /**
   * Get the string from the given bytes.
   *
   * @param bytes an array of bytes.
   * @param offset the start offset of the string data.
   * @param length the length of the string data.
   */
  public static String getString(byte[] bytes, int offset, int length) {
    return new String(Arrays.copyOfRange(bytes, offset, offset + length), UTF_8);
  }
}
