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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.TradefedInvocations;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Utility for fetching Tradefed invocation information from Tradefed process. */
public class TradefedInvocationsFetcher {

  /** The first group is device ID. */
  private static final Pattern INVOCATION_THREAD_HEADER_LINE_PATTERN =
      Pattern.compile("^\"Invocation-(.*?)\".*$");

  private static final Splitter LINE_SPLITTER = Splitter.on("\n");

  private final CommandExecutor commandExecutor;

  @Inject
  TradefedInvocationsFetcher(CommandExecutor commandExecutor) {
    this.commandExecutor = commandExecutor;
  }

  /**
   * Uses {@code jstack} to dump the stack trace of the given Tradefed process and then parses
   * invocation information from it.
   */
  public TradefedInvocations fetchTradefedInvocations(Path jstackPath, long pid)
      throws CommandException, InterruptedException {
    String stackTrace = getTradefedStackTrace(jstackPath, pid);
    ImmutableList<TradefedInvocation> invocations = parseTradefedStackTrace(stackTrace);
    return TradefedInvocations.newBuilder()
        .addAllInvocation(invocations)
        .setTimestamp(TimeUtils.toProtoTimestamp(Instant.now()))
        .build();
  }

  private String getTradefedStackTrace(Path jstackPath, long pid)
      throws CommandException, InterruptedException {
    return commandExecutor.run(
        Command.of(jstackPath.toString(), Long.toString(pid)).redirectStderr(false));
  }

  private static ImmutableList<TradefedInvocation> parseTradefedStackTrace(String stackTrace) {
    ImmutableList.Builder<TradefedInvocation> invocations = ImmutableList.builder();
    for (String line : LINE_SPLITTER.split(stackTrace)) {
      Matcher matcher = INVOCATION_THREAD_HEADER_LINE_PATTERN.matcher(line);
      if (matcher.matches()) {
        String deviceId = matcher.group(1);
        invocations.add(TradefedInvocation.newBuilder().setDeviceId(deviceId).build());
      }
    }
    return invocations.build();
  }
}
