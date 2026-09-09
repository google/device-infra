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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "local_model_factory",
    srcs = ["LocalModelFactory.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
    ],
)

java_library(
    name = "worker_event_dispatcher",
    srcs = ["WorkerEventDispatcher.java"],
    deps = [
        ":local_model_factory",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event:event_impl",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event:local_event_impl",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:injection_event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "plugin_worker_service_impl",
    srcs = ["PluginWorkerServiceImpl.java"],
    deps = [
        ":worker_event_dispatcher",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_grpc",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "@grpc-java//stub",
    ],
)

java_library(
    name = "plugin_worker_main_lib",
    srcs = ["PluginWorkerMain.java"],
    runtime_deps = [
        "@grpc-java//netty",
    ],
    deps = [
        ":plugin_worker_service_impl",
        ":worker_event_dispatcher",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin:plugin_loader",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:io_github_classgraph_classgraph",
    ],
)

java_binary(
    name = "plugin_worker_runner",
    hermetic = True,
    main_class = "com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker.PluginWorkerMain",
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":plugin_worker_main_lib",
        "@grpc-java//netty",
    ],
)
