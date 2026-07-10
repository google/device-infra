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

load("@rules_java//java:defs.bzl", "java_import", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "aapt",
    srcs = ["Aapt.java"],
    resources = [
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:android_device_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_build_spec_key",
    srcs = ["AndroidBuildSpecKey.java"],
    visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "android_packages",
    srcs = [
        "AndroidPackages.java",
    ],
    visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "perfetto",
    srcs = [
        "Perfetto.java",
        "RunPerfettoArgs.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/step/android:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "runtime_charging_util",
    srcs = ["RuntimeChargingUtil.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "serial_console_util",
    srcs = [
        "SerialConsole.java",
    ],
    visibility = ["//visibility:private"],
)

java_library(
    name = "wifi_util",
    srcs = ["WifiUtil.java"],
    resources = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/wifiutil:WifiUtil.apk",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
    deps = [
        ":aapt",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

# DDM utility that encapsulates DDMLib usage for capturing memory heap info.
# Depends on a customized, shaded ddmlib_jarjared target to avoid classpath collision.
java_library(
    name = "ddm_util",
    srcs = ["DdmUtil.java"],
    visibility = ["//:deviceinfra_all_pkg"],
    deps = [
        ":ddmlib_jarjared",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

# Imports the jarjared DDMLib jar into the build graph so ddm_util can depend on it natively.
java_import(
    name = "ddmlib_jarjared",
    jars = [":ddmlib_jarjar"],
    tags = [
    ],
    visibility = ["//src/javatests/com/google/wireless/qa/mobileharness/shared/android:__pkg__"],
)

# Packages raw DDMLib into an executable deploy jar so it can be shaded via the jarjar tool.
java_binary(
    name = "ddmlib_pre_jarjar",
    main_class = "none",
    visibility = ["//visibility:private"],
    runtime_deps = [
        "@maven//:com_android_tools_common",
        "@maven//:com_android_tools_ddms_ddmlib",
    ],
)

# Runs the jarjar tool on the packaged DDMLib to produce the shaded jar.
genrule(
    name = "ddmlib_jarjar",
    srcs = [
        ":ddmlib_pre_jarjar_deploy.jar",
        "ddmlib_jarjar_config.txt",
    ],
    outs = ["ddmlib_jarjar.jar"],
    cmd = "$(location //:jarjar_bin) process $(location ddmlib_jarjar_config.txt) $(location :ddmlib_pre_jarjar_deploy.jar) $@",
    tools = ["//:jarjar_bin"],
)
