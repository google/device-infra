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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Verify.verify;

import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Monitor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * An {@link OutputStream} that captures all bytes written to it in order to supply multiple
 * InputStreams that can stream over the bytes at their own pace.
 *
 * <p>This class ensures multiple input streams returned by {@link #openInputStream} may be read
 * from at the same time, but it does NOT ensure concurrent writes to the CapturingOutputStream or
 * concurrent reads from the individual input stream are thread safe.
 */
final class CapturingOutputStream extends OutputStream {
  // Same value as ArrayList.MAX_ARRAY_SIZE - see the comment there for details.
  private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

  // The monitor is used as a signaling mechanism between the CapturingOutputStream and its input
  // streams. When a CapturingInputStream is read and there are no bytes to be read and the output
  // stream is not closed, it waits on the monitor. When bytes are written to the output stream or
  // it is closed, it signals the monitor so that the input streams wake up from waiting.
  private final Monitor monitor = new Monitor();

  // The size must always be incremented *after* new bytes have been written to the data array, so
  // that the CapturedInputStreams never think more bytes are available than have been written.
  private volatile byte[] data = new byte[32];
  private volatile int size = 0;
  private volatile boolean closed;

  InputStream openInputStream() {
    return new CapturedInputStream();
  }

  byte[] toByteArray() {
    return Arrays.copyOf(data, size);
  }

  String toString(Charset charset) {
    return new String(data, 0, size, charset);
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void write(int b) throws IOException {
    ensureCapacityToWrite(1);
    data[size] = (byte) b;
    size += 1;
    signalBytesWrittenOrStreamClosed();
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    // checkPositionIndex throws IndexOutOfBoundsException, as required by write contract
    checkPositionIndex(off, b.length);
    checkPositionIndex(len, b.length - off, "len < 0 or off + len > b.length");
    ensureCapacityToWrite(len);
    System.arraycopy(b, off, data, size, len);
    size += len;
    signalBytesWrittenOrStreamClosed();
  }

  private void ensureCapacityToWrite(int numBytesToWrite) throws IOException {
    long minCapacity = LongMath.checkedAdd(size, numBytesToWrite);
    if (minCapacity > data.length) {
      long newCapacity = Math.min(Math.max(data.length * 2L, minCapacity), MAX_BUFFER_SIZE);
      if (newCapacity < minCapacity) {
        throw new IOException("Cannot allocate enough memory to capture all output");
      }
      data = Arrays.copyOf(data, Ints.checkedCast(newCapacity));
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    signalBytesWrittenOrStreamClosed();
  }

  /** Notifies waiting CapturedInputStreams that bytes have been written or the stream closed. */
  private void signalBytesWrittenOrStreamClosed() {
    monitor.enter();
    monitor.leave();
  }

  private final class CapturedInputStream extends InputStream {
    private int position = 0;
    private int mark = 0;

    private final Monitor.Guard bytesAvailableOrClosed =
        new Monitor.Guard(monitor) {
          @Override
          public boolean isSatisfied() {
            return position < size || closed;
          }
        };

    /**
     * Waits for any number of bytes to become available or the enclosing output stream to be closed
     * and returns the number of currently available bytes. If it returns zero, it means the output
     * stream is closed, so not only are there no bytes available, but none are forthcoming. The
     * implementation uses double-checked locking to return immediately without waiting if this
     * InputStream knows there are bytes available without acquiring a lock.
     */
    private int waitForBytes() throws IOException {
      if (!bytesAvailableOrClosed.isSatisfied()) {
        try {
          monitor.enterWhen(bytesAvailableOrClosed);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException(e);
        }
        monitor.leave();
      }
      return size - position;
    }

    @Override
    public int read() throws IOException {
      verifyPosition();
      return waitForBytes() == 0 ? -1 : data[position++];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      verifyPosition();
      checkElementIndex(off, b.length);
      checkPositionIndex(len, b.length - off);
      if (len == 0) {
        return 0;
      }

      int numBytesAvailable = waitForBytes();
      if (numBytesAvailable == 0) {
        return -1;
      }
      int numBytesRead = Math.min(numBytesAvailable, len);
      System.arraycopy(data, position, b, off, numBytesRead);
      position += numBytesRead;
      return numBytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
      verifyPosition();
      if (n <= 0) {
        return 0;
      }
      // waitForBytes returns int, so there are no overflow concerns with this cast.
      int numBytesSkipped = (int) Math.min(n, waitForBytes());
      position += numBytesSkipped;
      return numBytesSkipped;
    }

    @Override
    public int available() throws IOException {
      verifyPosition();
      return size - position;
    }

    @Override
    public void mark(int readlimit) {
      mark = position;
    }

    @Override
    public void reset() throws IOException {
      position = mark;
    }

    @Override
    public boolean markSupported() {
      return true;
    }

    private void verifyPosition() {
      verify(position >= 0);
      verify(position <= size);
    }
  }
}
