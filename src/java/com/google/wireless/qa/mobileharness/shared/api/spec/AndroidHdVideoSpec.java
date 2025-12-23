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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import java.time.Duration;

/** Spec for AndroidHdVideoDecorator. */
@SuppressWarnings("InterfaceWithOnlyStatics") // This interface is implemented by some decorators
public interface AndroidHdVideoSpec {

  @ParamAnnotation(
      required = false,
      help = "Whether to upload video when test pass. By default, it is true.")
  public static final String PARAM_VIDEO_ON_PASS = "video_on_pass";

  @ParamAnnotation(
      required = false,
      help =
          "The number of video clips. If it equals x, records screen at the last"
              + " (x*screenrecord_time_limit/60-screenrecord_time_limit/60,"
              + " x*screenrecord_time_limit/60] minutes. By default, it is 3 (recording at the last"
              + " 6-9 minutes). At least it's 2.")
  public static final String PARAM_NUM_VIDEO_CLIPS = "video_clip_num";

  @ParamAnnotation(
      required = false,
      help =
          "The video bit rate. The acceptable range is [100000, 100000000]. "
              + "By default, it is 4000000 (4Mbps).")
  public static final String PARAM_VIDEO_BIT_RATE = "video_bit_rate";

  @ParamAnnotation(required = false, help = "Custom prefix for video clip file names.")
  public static final String PARAM_CLIP_FILE_NAME = "clip_filename";

  @ParamAnnotation(
      required = false,
      help =
          "Whether or not to use the device ID as prefix for the clip file names. True by default.")
  public static final String PARAM_CLIP_FILENAME_USE_PREFIX = "clip_filename_use_prefix";

  @ParamAnnotation(
      required = false,
      help =
          "The video size: 1280x720. "
              + "The default value is the device's native display resolution "
              + "if supported, 1280x720 if not. "
              + "For best results, "
              + "use a size supported by your device's Advanced Video Coding (AVC) encoder.")
  public static final String PARAM_VIDEO_SIZE = "video_size";

  @ParamAnnotation(
      required = false,
      help = "Whether to add additional information on the video, such as timestamp overlay.")
  public static final String PARAM_BUGREPORT = "enable_screenrecord_bugreport";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to use VrCore RecorderService to record video. "
              + "Defaults to false; "
              + "set this to true if test runs in VR mode with VrCore as the compositor, "
              + "since the Android default screen recording does not work in this situation.")
  public static final String PARAM_RECORD_VR_VIDEO = "record_vr_video";

  @ParamAnnotation(
      required = false,
      help =
          "The maximum screen recording time for each video file, in seconds. If not set, it is 180"
              + " seconds.Set to 0 will remove the time limit, and generate only one video file."
              + " Only supported on API 34+. Use this param at your own risk of exhausting the"
              + " device's storage.")
  public static final String PARAM_SCREENRECORD_TIME_LIMIT_SECONDS =
      "screenrecord_time_limit_seconds";

  @ParamAnnotation(
      required = false,
      help =
          "Try capturing continuous video if available on device API level <= 33. This will create"
              + " a MPEGTS stream written to a file \"video.ts\" in the test's genFileDir.")
  public static final String PARAM_USE_CONTINUOUS_VIDEO = "use_continuous_video";

  /** The overlap recording time between two video clips. */
  public static final long OVERLAP_RECORDING_TIME_MS = Duration.ofSeconds(5L).toMillis();
}
