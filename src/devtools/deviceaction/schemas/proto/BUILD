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

# Config protos for devices and actions.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

proto_library(
    name = "base_spec_proto",
    srcs = ["base_spec.proto"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:base_spec_proto",
        "@com_google_protobuf//:descriptor_proto",
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "base_spec_java_proto",
    deps = [":base_spec_proto"],
)
