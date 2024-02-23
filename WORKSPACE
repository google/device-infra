workspace(name = "com_google_deviceinfra")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# FastCDC
http_archive(
    name = "fastcdc_go",
    build_file_content = """
load("@io_bazel_rules_go//go:def.bzl", "go_library")
package(
    default_visibility = ["//visibility:public"],
)
go_library(
    name = "fastcdc",
    srcs = ["fastcdc.go"],
    importpath = "fastcdc",
)""",
    # Supports large files (size of int64)
    patch_cmds = [
        "sed -i '1 i // Package fastcdc implements the FastCDC content-defined chunking algorithm.' fastcdc.go",
        "sed -i -e 's/\\([oO]ffset\\) int/\\1 int64/' -e 's/c.offset += chunk.Length/c.offset += int64(chunk.Length)/' fastcdc.go",
    ],
    sha256 = "fc618da339bb890ecedb54484bff5fc50f04e968ea470555c45eb3dfd5e5f093",
    strip_prefix = "fastcdc-go-0.2.0",
    urls = ["https://github.com/jotfs/fastcdc-go/archive/refs/tags/v0.2.0.zip"],
)

# GoogleTest/GoogleMock framework. Used by most unit-tests.
http_archive(
    name = "com_google_googletest",
    patches = ["//fcp/patches:googletest.patch"],
    sha256 = "fcfac631041fce253eba4fc014c28fd620e33e3758f64f8ed5487cc3e1840e3d",
    strip_prefix = "googletest-5a509dbd2e5a6c694116e329c5a20dc190653724",
    urls = ["https://github.com/google/googletest/archive/5a509dbd2e5a6c694116e329c5a20dc190653724.zip"],
)

# License rules.
http_archive(
    name = "rules_license",
    sha256 = "241b06f3097fd186ff468832150d6cc142247dc42a32aaefb56d0099895fd229",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_license/releases/download/0.0.8/rules_license-0.0.8.tar.gz",
        "https://github.com/bazelbuild/rules_license/releases/download/0.0.8/rules_license-0.0.8.tar.gz",
    ],
)

# Proto rules.
http_archive(
    name = "rules_proto",
    sha256 = "dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
    strip_prefix = "rules_proto-5.3.0-21.7",
    url = "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.7.tar.gz",
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# Python rules.
http_archive(
    name = "rules_python",
    sha256 = "d71d2c67e0bce986e1c5a7731b4693226867c45bfe0b7c5e0067228a536fc580",
    strip_prefix = "rules_python-0.29.0",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.29.0/rules_python-0.29.0.tar.gz",
)

load("@rules_python//python:repositories.bzl", "py_repositories")

py_repositories()

# Starlark libraries.
http_archive(
    name = "bazel_skylib",
    sha256 = "cd55a062e763b9349921f0f5db8c3933288dc8ba4f76dd9416aac68acee3cb94",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.5.0/bazel-skylib-1.5.0.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.5.0/bazel-skylib-1.5.0.tar.gz",
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

# Bazel tools.
http_archive(
    name = "bazel_tools",
    sha256 = "2676319e86c5aeab142dccd42434364a33aa330a091c13562b7de87a10e68775",
    urls = [
        "https://github.com/bazelbuild/bazel/releases/download/6.3.1/bazel-6.3.1-dist.zip",
    ],
)

# Gazelle and Go rules.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "6dc2da7ab4cf5d7bfc7c949776b1b7c733f05e56edc4bcd9022bb249d2e2a996",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.39.1/rules_go-v0.39.1.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.39.1/rules_go-v0.39.1.zip",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains(version = "1.20.5")

http_archive(
    name = "com_google_absl",
    sha256 = "81311c17599b3712069ded20cca09a62ab0bf2a89dfa16993786c8782b7ed145",
    strip_prefix = "abseil-cpp-20230125.1",
    # Committed on Jan 25, 2023.
    urls = [
        "https://github.com/abseil/abseil-cpp/archive/20230125.1.tar.gz",
    ],
)

http_archive(
    name = "com_github_grpc_grpc",
    sha256 = "8f01dac5a32104acbb76db1e6b447dc5b3dc738cb9bceeee01843d9d01d1d788",
    strip_prefix = "grpc-1.54.1",
    urls = ["https://github.com/grpc/grpc/archive/v1.54.1.zip"],
)

load("@com_github_grpc_grpc//bazel:grpc_deps.bzl", "grpc_deps")

grpc_deps()

http_archive(
    name = "bazel_gazelle",
    sha256 = "727f3e4edd96ea20c29e8c2ca9e8d2af724d8c7778e7923a854b2c80952bc405",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.30.0/bazel-gazelle-v0.30.0.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.30.0/bazel-gazelle-v0.30.0.tar.gz",
    ],
)

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")

gazelle_dependencies()

# Needed for the googleapis protos used by com_github_bazelbuild_remote_apis below.
http_archive(
    name = "googleapis",
    build_file = "//:builddeps/BUILD.bazel.googleapis",
    sha256 = "7b6ea252f0b8fb5cd722f45feb83e115b689909bbb6a393a873b6cbad4ceae1d",
    strip_prefix = "googleapis-143084a2624b6591ee1f9d23e7f5241856642f4d",
    urls = [
        "https://github.com/googleapis/googleapis/archive/143084a2624b6591ee1f9d23e7f5241856642f4d.zip",
    ],
)

load("//:builddeps/rbe_go_deps.bzl", "rbe_go_deps")

# gazelle:repository_macro builddeps/rbe_go_deps.bzl%rbe_go_deps
rbe_go_deps()

# Android Bazel rules
http_archive(
    name = "rules_android_sdk",
    sha256 = "cd06d15dd8bb59926e4d65f9003bfc20f9da4b2519985c27e190cddc8b7a7806",
    strip_prefix = "rules_android-0.1.1",
    urls = ["https://github.com/bazelbuild/rules_android/archive/v0.1.1.zip"],
)

load("@rules_android_sdk//android:rules.bzl", "android_sdk_repository")

android_sdk_repository(
    name = "androidsdk",
)

http_archive(
    name = "rules_android_ndk",
    sha256 = "3fa4a58f4df356bca277219763f91c64f33dcc59e10843e9762fc5e7947644f9",
    strip_prefix = "rules_android_ndk-63fa7637902fb1d7db1bf86182e939ed3fe98477",
    url = "https://github.com/bazelbuild/rules_android_ndk/archive/63fa7637902fb1d7db1bf86182e939ed3fe98477.zip",
)

load("@rules_android_ndk//:rules.bzl", "android_ndk_repository")

android_ndk_repository(
    name = "androidndk",
)

# gRPC
http_archive(
    name = "io_grpc_grpc_java",
    sha256 = "301e0de87c7659cc790bd2a7265970a71632d55773128c98768385091c0a1a97",
    strip_prefix = "grpc-java-1.61.0",
    url = "https://github.com/grpc/grpc-java/archive/v1.61.0.zip",
)

http_archive(
    name = "rules_jvm_external",
    sha256 = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca",
    strip_prefix = "rules_jvm_external-4.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/4.2.zip",
)

load("@io_grpc_grpc_java//:repositories.bzl", "IO_GRPC_GRPC_JAVA_ARTIFACTS", "IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS", "grpc_java_repositories")

grpc_java_repositories()

load("@com_google_protobuf//:protobuf_deps.bzl", "PROTOBUF_MAVEN_ARTIFACTS", "protobuf_deps")

protobuf_deps()

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "com_google_absl_py",
    sha256 = "a96b4fae80ccb6d393f1344dc47452dc4b557cc8f2e70483c3908d74999290f1",
    strip_prefix = "abseil-py-c5c609cf04ea3f46eb620eb1b948ee2294645c4a",
    url = "https://github.com/abseil/abseil-py/archive/c5c609cf04ea3f46eb620eb1b948ee2294645c4a.zip",
)

http_archive(
    name = "build_bazel_rules_swift",
    sha256 = "bf2861de6bf75115288468f340b0c4609cc99cc1ccc7668f0f71adfd853eedb3",
    url = "https://github.com/bazelbuild/rules_swift/releases/download/1.7.1/rules_swift.1.7.1.tar.gz",
)

http_archive(
    name = "com_google_googleapis",
    sha256 = "70cdef593fbfe340d558ca10c6858b5c0410a54576381c422dc3b9158a12ba03",
    strip_prefix = "googleapis-18becb1d1426feb7399db144d7beeb3284f1ccb0",
    urls = ["https://github.com/googleapis/googleapis/archive/18becb1d1426feb7399db144d7beeb3284f1ccb0.tar.gz"],
)

load("@com_google_googleapis//:repository_rules.bzl", "switched_rules_by_language")

# Initialize Google APIs with only C++ and Python targets
switched_rules_by_language(
    name = "com_google_googleapis_imports",
    cc = True,
    grpc = True,
    python = True,
)

maven_install(
    artifacts = [
        "androidx.annotation:annotation:1.2.0",
        "androidx.core:core:1.6.0",
        "com.beust:jcommander:1.82",
        "com.google.api-client:google-api-client:1.35.2",
        "com.google.apis:google-api-services-storage:v1-rev20211201-1.32.1",
        "com.google.auto.value:auto-value:1.10.4",
        "com.google.auto.value:auto-value-annotations:1.10.4",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.code.gson:gson:2.10.1",
        "com.google.errorprone:error_prone_annotations:2.24.1",
        "com.google.flogger:flogger-system-backend:0.8",
        "com.google.flogger:flogger:0.8",
        "com.google.guava:guava:33.0.0-jre",
        "com.google.http-client:google-http-client:1.43.0",
        "com.google.http-client:google-http-client-gson:1.43.0",
        "com.google.inject.extensions:guice-assistedinject:5.1.0",
        "com.google.inject.extensions:guice-testlib:5.1.0",
        "com.google.inject:guice:5.1.0",
        "com.google.testparameterinjector:test-parameter-injector:1.15",
        "com.google.truth.extensions:truth-proto-extension:1.4.0",
        "com.google.truth.extensions:truth-java8-extension:1.4.0",
        "com.google.truth:truth:1.4.0",
        "commons-io:commons-io:2.15.1",
        "info.picocli:picocli:4.7.5",
        "javax.inject:jsr330-api:0.9",
        "junit:junit:4.13.2",
        "net.bytebuddy:byte-buddy:1.14.11",
        "net.sf.kxml:kxml2:2.3.0",
        "org.apache.commons:commons-lang3:3.14.0",
        "org.jline:jline:3.25.1",
        "org.json:json:20231013",
        "org.mockito:mockito-core:4.11.0",
        "org.reflections:reflections:0.9.10",
        "org.robolectric:android-all:14-robolectric-10818077",
        "org.yaml:snakeyaml:2.2",
        "xmlpull:xmlpull:1.1.3.1",
    ] + IO_GRPC_GRPC_JAVA_ARTIFACTS + PROTOBUF_MAVEN_ARTIFACTS,
    fetch_sources = True,
    generate_compat_repositories = True,
    override_targets = IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
        "https://repository.mulesoft.org/nexus/content/repositories/public",
    ],
)

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()
