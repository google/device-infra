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
    name = "fastboot",
    srcs = ["Fastboot.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//src/javatests/com/google/devtools/deviceinfra/platform/android/sdk/fastboot:__pkg__",
    ],
    exports = [":enums"],
    deps = [
        ":enums",
        "//src/devtools/mobileharness/shared/util/quota/proto:quota_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot/initializer:fastboot_initializer",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot/initializer:fastboot_param",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:device_constant",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/quota",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_commons_commons_lang3",
    ],
)

java_library(
    name = "enums",
    srcs = ["Enums.java"],
    visibility = [
        "//:deviceinfra_devicemanagement_pkg",
    ],
)
