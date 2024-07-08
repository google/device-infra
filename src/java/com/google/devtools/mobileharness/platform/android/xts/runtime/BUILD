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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "tradefed_invocations_fetcher",
    srcs = ["TradefedInvocationsFetcher.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/runtime:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/platform/android/xts/proto:runtime_info_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info_util",
    srcs = ["XtsTradefedRuntimeInfoUtil.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/platform/android/xts/proto:runtime_info_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info_file_util",
    srcs = ["XtsTradefedRuntimeInfoFileUtil.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/platform/android/xts/proto:runtime_info_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
    ],
)
