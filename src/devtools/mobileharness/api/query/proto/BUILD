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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

proto_library(
    name = "device_query_proto",
    srcs = ["device_query.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
    ],
)

java_proto_library(
    name = "device_query_java_proto",
    deps = [":device_query_proto"],
)

proto_library(
    name = "lab_query_proto",
    srcs = ["lab_query.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "lab_query_java_proto",
    deps = [":lab_query_proto"],
)
