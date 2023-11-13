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
    name = "adb_initializer",
    srcs = ["AdbInitializer.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb:__subpackages__",
    ],
    deps = [
        ":adb_initialize_template",
        ":adb_initialize_template_impl",
        "//src/devtools/mobileharness/platform/android/sdktool/proto:adb_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "adb_initializer_helper",
    srcs = ["AdbInitializerHelper.java"],
    visibility = [
        "//src/javatests/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb/initializer:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
    ],
)

java_library(
    name = "adb_initialize_template",
    srcs = ["AdbInitializeTemplate.java"],
    visibility = [
    ],
    deps = [
        "//src/devtools/mobileharness/platform/android/sdktool/proto:adb_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "adb_initialize_template_impl",
    srcs = ["AdbInitializeTemplateImpl.java"],
    visibility = [
        "//src/javatests/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb/initializer:__pkg__",
    ],
    deps = [
        ":adb_initialize_template",
        ":adb_initializer_helper",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@maven//:com_google_guava_guava",
    ],
)
