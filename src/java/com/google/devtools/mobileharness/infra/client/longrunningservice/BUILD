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
    name = "annotations",
    srcs = ["Annotations.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__subpackages__",
    ],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "olc_server",
    srcs = ["OlcServer.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [
        ":server_module",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_recorder",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:control_service",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:session_service",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:version_service",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//netty",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "server_module",
    srcs = ["ServerModule.java"],
    deps = [
        ":annotations",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:module",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:controller_module",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
