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
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
    ],
)

proto_library(
    name = "monitored_record_proto",
    srcs = ["monitored_record.proto"],
    deps = ["@com_google_protobuf//:timestamp_proto"],
)

java_proto_library(
    name = "monitored_record_java_proto",
    deps = [":monitored_record_proto"],
)

proto_library(
    name = "record_proto",
    srcs = ["record.proto"],
)
