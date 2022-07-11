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

DEFAULT_VISIBILITY = [
    "//devtools/common/metrics/stability/user:all_users",
    "//devtools/common/metrics/stability/model/proto:__subpackages__",
    "//java/com/google/devtools/common/metrics/stability:__subpackages__",
    "//javatests/com/google/devtools/common/metrics/stability:__subpackages__",
    "//src/java/com/google/devtools/common/metrics/stability:__subpackages__",
    "//src/javatests/com/google/devtools/common/metrics/stability:__subpackages__",
]

package(default_visibility = DEFAULT_VISIBILITY)

proto_library(
    name = "error_proto",
    srcs = [
        "error_id.proto",
        "error_type.proto",
        "exception.proto",
        "namespace.proto",
    ],
)

# For open source projects only. Internal google3 users should use
# //devtools/common/metrics/stability/model/proto:error_java_proto instead.
java_proto_library(
    name = "error_java_proto",
    deps = [":error_proto"],
)
