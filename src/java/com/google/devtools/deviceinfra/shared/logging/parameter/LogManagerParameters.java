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

package com.google.devtools.deviceinfra.shared.logging.parameter;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Parameters for Udcluster Host Logging.
 *
 * <p>Example 1, Store the logs of Daemon Server in prod env to a local file and also upload them to
 * Stackdriver.
 *
 * <pre class="code"><code class="java">
 *   LogManagerParameters.newBuilder()
 *   .setFileLogger("/foo/bar/daemon_log_dir")
 *   .setLogProject(LogProject.Daemon)
 *   .setLogEnvironment(LogEnvironment.PROD)
 *   .setLogUploaderParameters(StackdriverLogUploaderParameters.of(LogProject.Daemon));
 *   </code><</pre>
 *
 * <p>Example 2, Upload the logs to specified server, but don't store them locally.
 *
 * <pre class="code"><code class="java">
 *   LogManagerParameters.newBuilder()
 *   .setLogProject(LogProject.TaskManager)
 *   .setLogUploaderParameters(TaskManagerLogUploaderParameters.getInstance());
 *   </code><</pre>
 */
@AutoValue
public abstract class LogManagerParameters {

  /** The local file dir to storage the logs. */
  public abstract Optional<String> fileLogger();

  /** The Stackdriver log project in GCP. */
  public abstract LogProject logProject();

  /** The environment of the server or binary generating the log. */
  public abstract LogEnvironment logEnvironment();

  /** The parameters to generate the log uploader. */
  public abstract LogUploaderParameters logUploaderParameters();

  public static Builder newBuilder() {
    return new AutoValue_LogManagerParameters.Builder().setLogEnvironment(LogEnvironment.UNKNOWN);
  }

  /** Builder for {@link LogManagerParameters} */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Optional. */
    public abstract Builder setFileLogger(String fileLogger);

    /** Required. */
    public abstract Builder setLogProject(LogProject logProject);

    public abstract Builder setLogEnvironment(LogEnvironment logEnvironment);

    /* Required. */
    public abstract Builder setLogUploaderParameters(LogUploaderParameters logUploaderParameters);

    public abstract LogManagerParameters build();
  }
}
