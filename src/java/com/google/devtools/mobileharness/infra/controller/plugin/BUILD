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

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "common_setup_module",
    srcs = ["CommonSetupModule.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/service/gateway/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:event_scoped",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:injection_event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "lab_common_setup_module",
    srcs = ["LabCommonSetupModule.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__"],
    deps = [
        ":common_setup_module",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:event_scoped",
    ],
)

java_library(
    name = "plugin_creator",
    srcs = [
        "PluginCreator.java",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        ":plugin_loader",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/loader:plugin_instantiator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/provider:plugin_class_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:org_reflections_reflections",
    ],
)

java_library(
    name = "plugin_loader",
    srcs = ["PluginLoader.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
