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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "message_event_impl",
    srcs = ["MessageEventImpl.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/messaging:messaging_subscriber",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "message_receptions_handler",
    srcs = ["MessageReceptionsHandler.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__subpackages__",
    ],
    deps = ["//src/devtools/mobileharness/api/messaging:messaging_java_proto"],
)

java_library(
    name = "message_sender",
    srcs = ["MessageSender.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/container/testengine/controller:__pkg__",
        "//java/com/google/wireless/qa/mobileharness/client/api:__pkg__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__subpackages__",
    ],
    deps = [
        ":message_receptions_handler",
        ":message_subscriber_backend",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "message_sender_finder",
    srcs = ["MessageSenderFinder.java"],
    visibility = [
        "//:omnilab_fusion",
        "//java/com/google/devtools/mobileharness/infra/container/testengine/controller:__pkg__",
        "//java/com/google/wireless/qa/mobileharness/client/api:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__subpackages__",
    ],
    deps = [
        ":message_sender",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/messaging:exception",
    ],
)

java_library(
    name = "message_subscriber_backend",
    srcs = ["MessageSubscriberBackend.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__subpackages__",
    ],
    deps = [
        ":message_event_impl",
        ":message_receptions_handler",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/api/messaging:messaging_subscriber",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_reflection_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "messaging_manager",
    srcs = ["MessagingManager.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__subpackages__",
    ],
    deps = [
        ":annotations",
        ":message_sender",
        ":message_sender_finder",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/messaging:exception",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "messaging_manager_holder",
    srcs = ["MessagingManagerHolder.java"],
    visibility = [
        "//:omnilab_fusion",
        "//java/com/google/devtools/mobileharness/infra/container/testengine:__pkg__",
        "//java/com/google/devtools/mobileharness/service/gateway/modules:__pkg__",
        "//src/java/com/google/devtools/mobileharness/api/messaging:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
    ],
    deps = [
        ":message_sender_finder",
        ":messaging_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "messaging_service",
    srcs = ["MessagingService.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        ":messaging_manager",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/devtools/mobileharness/api/messaging:messaging_service_java_grpc",
        "//src/devtools/mobileharness/api/messaging:messaging_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@grpc-java//stub",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "messaging_service_module",
    srcs = ["MessagingServiceModule.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        ":annotations",
        ":messaging_service",
        "//src/devtools/mobileharness/api/messaging:messaging_service_java_grpc",
        "//src/java/com/google/devtools/mobileharness/infra/lab:annotations",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/messaging:__pkg__",
    ],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)
