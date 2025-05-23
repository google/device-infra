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

package com.google.devtools.mobileharness.platform.android.xts.agent.testdata;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.android.tradefed.cluster.FakeClusterCommandSchedulerUtil;
import com.android.tradefed.invoker.TestInvocation;
import com.google.common.collect.ImmutableList;
import java.util.stream.IntStream;

public class FakeTradefed {

  public static final String DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION =
      "device_id_to_trigger_invocation_checked_exception";
  public static final String DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION =
      "device_id_to_trigger_invocation_unchecked_exception";
  public static final String INVOCATION_CHECKED_EXCEPTION_MESSAGE =
      "Fake tradefed invocation checked exception.";
  public static final String INVOCATION_UNCHECKED_EXCEPTION_MESSAGE =
      "Fake tradefed invocation unchecked exception.";

  public static void main(String[] args) throws InterruptedException {
    ImmutableList<String> deviceSerials =
        IntStream.range(0, args.length - 1)
            .filter(i -> args[i].equals("-s"))
            .mapToObj(i -> args[i + 1])
            .collect(toImmutableList());

    boolean sharding = stream(args).anyMatch(arg -> arg.equals("--shard-count"));

    try {
      if (sharding) {
        // For simplicity of this fake implementation, we're ignoring the shard count param value.
        deviceSerials.forEach(
            deviceSerial ->
                // Spawning new threads so that the invocations will be running in parallel.
                new Thread(
                        () -> {
                          try {
                            new TestInvocation()
                                .invoke(
                                    // In real implementation, the invocation ID is the same as the
                                    // command ID (with values being 1, 2, etc.).
                                    new FakeInvocationContext(
                                        /* invocationId= */ "1", ImmutableList.of(deviceSerial)),
                                    0,
                                    0,
                                    0);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // Restore interrupt status
                          }
                        })
                    .start());
        // Briefly sleeps to make sure the test will be able to read the row generated by the
        // monitoring agent after exiting the invocation.
        Thread.sleep(4_000L);
      } else {
        new TestInvocation()
            .invoke(new FakeInvocationContext(/* invocationId= */ "1", deviceSerials), 0, 0, 0);
        // Sleep time is shorter compared to the sharding case, since the fake invocation is running
        // seqentially (vs. in parallel for the sharding case).
        Thread.sleep(2_000L);
      }
    } catch (RuntimeException e) {
      if (!e.getMessage().equals(INVOCATION_UNCHECKED_EXCEPTION_MESSAGE)) {
        throw e;
      }
    }

    // Completes the invocation.
    if (sharding) {
      for (String deviceSerial : deviceSerials) {
        FakeClusterCommandSchedulerUtil.invocationComplete(
            new FakeInvocationContext(/* invocationId= */ "1", ImmutableList.of(deviceSerial)));
      }
    } else {
      FakeClusterCommandSchedulerUtil.invocationComplete(
          new FakeInvocationContext(/* invocationId= */ "1", deviceSerials));
    }

    // Briefly sleeps to make sure the test will be able to read the row generated by the monitoring
    // agent after the `invocationComplete` method invocation.
    Thread.sleep(2_000L);
  }

  private FakeTradefed() {}
}
