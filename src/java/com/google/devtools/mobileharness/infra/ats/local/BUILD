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
        "//src/java/com/google/devtools/mobileharness/infra/ats/local:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/local:__subpackages__",
    ],
)

java_binary(
    name = "ats_local_runner",
    jvm_flags = ["--add-opens=java.base/java.lang=ALL-UNNAMED"],
    main_class = "com.google.devtools.mobileharness.infra.ats.local.AtsLocalRunner",
    runtime_deps = [
        ":ats_local_runner_lib",
    ],
)

java_library(
    name = "ats_local_runner_lib",
    srcs = ["AtsLocalRunner.java"],
    deps = [
        ":ats_local_runner_module",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/ats/local/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:device_infra_service_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:builtin_olc_server_flags",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "ats_local_runner_module",
    srcs = ["AtsLocalRunnerModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_environment_preparer",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
