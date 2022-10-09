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

package com.google.wireless.qa.mobileharness.shared.log.testing;

import static org.mockito.Mockito.mock;

import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.log.LogCollectorBackend;
import com.google.wireless.qa.mobileharness.shared.log.LogContext;
import com.google.wireless.qa.mobileharness.shared.log.LogData;
import com.google.wireless.qa.mobileharness.shared.log.LoggingApi;
import com.google.wireless.qa.mobileharness.shared.log.testing.FakeLogCollector.Api;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Fake {@link LogCollector} for testing.
 *
 * <p>Example: If you want to test a method <code>{@code foo.bar(int x, LogCollector<?> log)}</code>
 * and verify log collecting actions of it, you can do it like:
 *
 * <pre>
 * <b>FakeLogCollector log = new FakeLogCollector();</b>
 * foo.bar(x, <b>log</b>);
 * verify(<b>log.getMockHandler()</b>).log(Level.INFO, "Message goo", exceptionToVerify);</pre>
 *
 * <p>It passes if you implement <code>{@code Foo.bar(int x, LogCollector<?> log)}</code> like:
 *
 * <pre>{@code
 * public void bar(int x, LogCollector<?> log) {
 *   ...
 *   log.atInfo().withCause(exceptionToVerify).log("Message %s", "goo");
 *   ...
 * }
 * }</pre>
 */
public final class FakeLogCollector implements LogCollector<Api> {

  /**
   * @see LoggingApi
   */
  public interface Api extends LoggingApi<Api> {}

  /** Log handler for verifying actions on the collector. */
  public interface LogHandler {

    /** A method which will be invoked when logging a message on the collector. */
    void log(Level level, String formattedMessage, @Nullable Throwable cause);
  }

  private class LoggingApiImpl extends LogContext<Api, LogData> implements Api {

    private LoggingApiImpl(Level level) {
      super(level);
    }

    @Override
    protected LogCollectorBackend<LogData> getBackend() {
      return backend;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected LogData data() {
      return this;
    }
  }

  private class LogCollectorBackendImpl implements LogCollectorBackend<LogData> {

    @Override
    public void log(LogData data) {
      logHandler.log(data.getLevel(), data.getFormattedMessage(), data.getCause().orElse(null));
    }
  }

  private final LogCollectorBackend<LogData> backend = new LogCollectorBackendImpl();

  private final LogHandler logHandler = mock(LogHandler.class);

  @Override
  public Api at(Level level) {
    return new LoggingApiImpl(level);
  }

  /** Gets the mock log handler for verifying actions on the collector. */
  public LogHandler getMockHandler() {
    return logHandler;
  }
}
