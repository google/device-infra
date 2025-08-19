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

load("@rules_java//java:defs.bzl", "java_library", "java_test")
load("@rules_java//java:java_binary.bzl", "java_binary")

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "genericservice",
    srcs = ["GenericServiceManager.java"],
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    runtime_deps = [
        "@io_grpc_grpc_java//netty",
    ],
    deps = [
        "//google/protobuf:struct_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//third_party/chromiumos_config/proto/chromiumos/test/api:chromiumos_test_api_java_grpc",
        "//third_party/chromiumos_config/proto/chromiumos/test/api:chromiumos_test_api_java_proto",
        "//third_party/chromiumos_config/proto/chromiumos/test/lab/api:chromiumos_test_lab_api_java_proto",
        "@com_google_protobuf_protobuf_java",
        "@io_grpc_grpc_java//core",
    ],
)

java_binary(
    name = "GenericServiceManager",
    main_class = "com.google.devtools.mobileharness.platform.android.genericservice.GenericServiceManager",
    runtime_deps = [":genericservice"],
)

java_test(
    name = "GenericServiceManagerTest",
    srcs = ["GenericServiceManagerTest.java"],
    test_class = "com.google.devtools.mobileharness.platform.android.genericservice.GenericServiceManagerTest",
    runtime_deps = [
        "@io_grpc_grpc_java//netty",
    ],
    deps = [
        ":genericservice",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//third_party/chromiumos_config/proto/chromiumos/test/api:chromiumos_test_api_java_grpc",
        "//third_party/chromiumos_config/proto/chromiumos/test/api:chromiumos_test_api_java_proto",
        "@com_google_protobuf_protobuf_java",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
        "@maven//:junit_junit",
    ],
)
