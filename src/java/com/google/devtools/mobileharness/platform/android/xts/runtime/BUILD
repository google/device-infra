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

load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")

package(default_applicable_licenses = ["//:license"])

# A java_binary target is used to easily generate a "deploy jar".
# This deploy jar contains all the classes of the Gson dependency, bridging the restricted
# visibility of the internal libgson.jar.
java_binary(
    name = "gson_for_shading",
    main_class = "none",
    runtime_deps = ["@maven//:com_google_code_gson_gson"],
)

# A genrule to shade the Gson dependency using Jarjar.
# This prevents classpath conflicts with Tradefed's own Gson library when the
# Mobile Harness Tradefed agent is injected into a Tradefed process.
# We rewrite the package from `com.google.gson` to a Mobile Harness specific package.
genrule(
    name = "shade_gson_rule",
    srcs = [
        ":gson_for_shading_deploy.jar",
    ],
    outs = ["shaded_gson.jar"],
    # The `cmd` logic:
    # 1. `echo ... > jarjar_rules.txt`: Creates a Jarjar rules file that renames
    #    all `com.google.gson` packages to a Mobile Harness subpackage.
    # 2. `$(location ...) process ...`: Runs Jarjar to apply the rules to the
    #    input JAR and produce the shaded output JAR.
    cmd = "echo 'rule com.google.gson.** com.google.devtools.mobileharness.platform.android.xts.runtime.shaded.gson.@1' > $(@D)/jarjar_rules.txt && " +
          "$(location //:jarjar_bin) process $(@D)/jarjar_rules.txt $< $@",
    tools = ["//:jarjar_bin"],
)

java_import(
    name = "gson_shaded",
    jars = ["shaded_gson.jar"],
)

java_library(
    name = "xts_tradefed_runtime_info_file_util",
    srcs = ["XtsTradefedRuntimeInfoFileUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        ":xts_tradefed_runtime_info",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info",
    srcs = ["XtsTradefedRuntimeInfo.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/runtime:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        ":gson_shaded",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info_monitor",
    srcs = ["XtsTradefedRuntimeInfoMonitor.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
    ],
    deps = [
        ":xts_tradefed_runtime_info",
        ":xts_tradefed_runtime_info_file_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "xts_tradefed_test_module_results_monitor",
    srcs = ["XtsTradefedTestModuleResultsMonitor.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/runtime:__pkg__",
    ],
    deps = [
        ":xts_tradefed_test_module_results",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)

java_library(
    name = "xts_tradefed_test_module_results",
    srcs = ["XtsTradefedTestModuleResults.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts:__subpackages__",
    ],
    deps = [
        ":gson_shaded",
    ],
)
