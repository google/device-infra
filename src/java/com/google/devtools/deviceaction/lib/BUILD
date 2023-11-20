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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
    ],
)

java_library(
    name = "lib",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:install_mainline_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:reset_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/java/com/google/devtools/deviceaction/framework",
        "//src/java/com/google/devtools/deviceaction/framework:device_action_module",
        "//src/java/com/google/devtools/deviceaction/framework/actions",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/quota",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
