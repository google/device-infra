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
    "//java/com/google/devtools/common/metrics/stability:__subpackages__",
    "//javatests/com/google/devtools/common/metrics/stability:__subpackages__",
    "//src/java/com/google/devtools/common/metrics/stability:__subpackages__",
    "//src/javatests/com/google/devtools/common/metrics/stability:__subpackages__",
]

package(default_visibility = DEFAULT_VISIBILITY)

# For open source projects only. Internal google3 users should use
# //java/com/google/devtools/common/metrics/stability/model:error_id
# instead.
java_library(
    name = "error_id",
    srcs = [
        "ErrorId.java",
        "ErrorIdProvider.java",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
    ],
)
