# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

java_library(
    name = "impl",
    srcs = [
        "DeviceDaemonApkInfoImplV1.java",
        "DeviceDaemonApkInfoImplV2.java",
    ],
    resources = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/v1:daemon.apk",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/v2:daemon.apk",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:device_daemon_apk_info_interface",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
