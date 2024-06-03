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

package com.google.devtools.mobileharness.shared.util.time;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import java.time.Duration;

/** The util class for calculating the offset of test runners in different components. */
public class TimeoutUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The offset time between test timeout in client and test timeout in lab server. */
  public static final Duration DEFAULT_OFFSET_TIME_OF_LAB_SERVER_TR_EARLIER_THAN_CLIENT_TR =
      Duration.ofSeconds(10);

  /** The up limit time between blaze test command timeout and OmniLab job/test timeout. */
  public static final Duration UPPER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE =
      Duration.ofMinutes(10);

  /** The lower limit time between blaze test command timeout and OmniLab start timeout. */
  public static final Duration LOWER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE =
      Duration.ofSeconds(60);

  /**
   * Reduces the job timeout to make sure it is smaller enough than the blaze timeout to give time
   * for blaze uploading data.
   *
   * @param jobTimeoutFromConfig: the job timeout set via the job config
   * @param blazeTimeout: the timeout of the blaze command
   * @return the finalized job timeout to make sure it is smaller enough than the blaze timeout.
   */
  public static Duration finalizeJobTimeout(Duration jobTimeoutFromConfig, Duration blazeTimeout) {
    Duration jobTimeoutUpperLimit =
        blazeTimeout.minus(getOffsetTimeOfOmniLabTestRunnerEarlierThanBlazeTimeout(blazeTimeout));
    if (jobTimeoutFromConfig.compareTo(jobTimeoutUpperLimit) > 0) {
      logger.atWarning().log(
          "Mobile Harness job timeout %s is greater than or too close to Blaze timeout %s, \n"
              + "Job may be killed by Blaze before finish. \n"
              + "Decrease job timeout to %s.",
          jobTimeoutFromConfig, blazeTimeout, jobTimeoutUpperLimit);
      return jobTimeoutUpperLimit;
    } else {
      return jobTimeoutFromConfig;
    }
  }

  /**
   * Reduces the test timeout to make sure it is smaller enough than the blaze timeout to give time
   * for blaze uploading data.
   *
   * @param testTimeoutFromConfig: the test timeout set via the job config
   * @param blazeTimeout: the timeout of the blaze command
   * @return the finalized test timeout to make sure it is smaller enough than the blaze timeout.
   */
  public static Duration finalizeTestTimeout(
      Duration testTimeoutFromConfig, Duration blazeTimeout) {
    Duration testTimeoutUpperLimit =
        blazeTimeout.minus(getOffsetTimeOfOmniLabTestRunnerEarlierThanBlazeTimeout(blazeTimeout));
    if (testTimeoutFromConfig.compareTo(testTimeoutUpperLimit) > 0) {
      logger.atWarning().log(
          "Mobile Harness test timeout %s is greater than or too close to Blaze timeout %s, \n"
              + "test may be killed by Blaze before finish. \n"
              + "Decrease test timeout to %s.  ",
          testTimeoutFromConfig, blazeTimeout, testTimeoutUpperLimit);
      return testTimeoutUpperLimit;
    } else {
      return testTimeoutFromConfig;
    }
  }

  /**
   * Reduces the start timeout to make sure it is smaller enough than the blaze timeout to give time
   * to i) MH job loading plugins, and ii) blaze uploading data.
   *
   * @param startTimeoutFromConfig the start timeout set via the job config
   * @param blazeTimeout the timeout of the blaze command
   * @return the finalized start timeout to make sure it is smaller enough than the blaze timeout.
   */
  public static Duration finalizeStartTimeout(
      Duration startTimeoutFromConfig, Duration blazeTimeout) {
    Duration startTimeoutUpperLimit =
        blazeTimeout.minus(getOffsetTimeOfOmniLabStartTimeoutEarlierThanBlazeTimeout(blazeTimeout));
    if (startTimeoutFromConfig.compareTo(startTimeoutUpperLimit) > 0) {
      logger.atWarning().log(
          "Mobile Harness start timeout %s is greater than or too close to Blaze timeout %s, \n"
              + "test may be killed by Blaze before finish. \n"
              + "Decrease start timeout to %s.  ",
          startTimeoutFromConfig, blazeTimeout, startTimeoutUpperLimit);
      return startTimeoutUpperLimit;
    } else {
      return startTimeoutFromConfig;
    }
  }

  /**
   * Reduces the timeout on the lab server side to make sure it gives time for client the pull back
   * the files generated on the lab server.
   *
   * @param clientSideTestTimeout the timeout on the client side
   * @return the timeout on the lab server side
   */
  public static Timeout finalizeLabServerTestTimeout(Timeout clientSideTestTimeout) {
    Duration offset =
        getOffsetTimeOfLabServerTestRunnerEarlierThanClientTestRunner(
            clientSideTestTimeout.testTimeout());
    return Timeout.newBuilder()
        .setTestTimeout(clientSideTestTimeout.testTimeout().minus(offset))
        .setJobTimeout(clientSideTestTimeout.jobTimeout())
        .setStartTimeout(clientSideTestTimeout.startTimeout())
        .build();
  }

  /**
   * Gets the offset time between the blaze test command timeout and OmniLab start timeout. It is
   * the max value of the lower limit offset and 1% of the blaze test command timeout.
   */
  private static Duration getOffsetTimeOfOmniLabStartTimeoutEarlierThanBlazeTimeout(
      Duration blazeTimeout) {
    Duration onePercent = blazeTimeout.dividedBy(100);
    if (LOWER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE.compareTo(onePercent) < 0) {
      return onePercent;
    } else {
      return LOWER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE;
    }
  }

  /**
   * Gets the offset time between the test timeout in client and test timeout in lab server. It is
   * the max value of the default offset and 1% of the test timeout.
   */
  private static Duration getOffsetTimeOfLabServerTestRunnerEarlierThanClientTestRunner(
      Duration testTimeout) {
    Duration onePercent = testTimeout.dividedBy(100);
    if (DEFAULT_OFFSET_TIME_OF_LAB_SERVER_TR_EARLIER_THAN_CLIENT_TR.compareTo(onePercent) < 0) {
      return onePercent;
    } else {
      return DEFAULT_OFFSET_TIME_OF_LAB_SERVER_TR_EARLIER_THAN_CLIENT_TR;
    }
  }

  /**
   * Gets the offset time between the blaze test command timeout and OmniLab job/test timeout. It is
   * the min value of the upper limit offset and 1% of the blaze test command timeout.
   */
  private static Duration getOffsetTimeOfOmniLabTestRunnerEarlierThanBlazeTimeout(
      Duration blazeTimeout) {
    Duration onePercent = blazeTimeout.dividedBy(100);
    if (UPPER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE.compareTo(onePercent) < 0) {
      return UPPER_LIMIT_OFFSET_TIME_OF_OMNILAB_TR_EARLIER_THAN_BLAZE;
    } else {
      return onePercent;
    }
  }

  private TimeoutUtil() {}
}
