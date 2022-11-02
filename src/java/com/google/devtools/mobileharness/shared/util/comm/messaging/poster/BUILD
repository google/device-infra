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

java_library(
    name = "poster",
    srcs = ["TestMessagePoster.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//java/com/google/devtools/mobileharness/infra/container/testengine/controller:__pkg__",
        "//java/com/google/wireless/qa/mobileharness/client/api:__pkg__",
        "//java/com/google/wireless/qa/mobileharness/shared/comm/message:__pkg__",
        "//javatests/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/client/api:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/client/api/mode/remote:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/comm/message:__pkg__",
        "//src/java/com/google/devtools/deviceinfra/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
    ],
)
