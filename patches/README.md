# Patches for Bazel External Dependencies

This directory contains patch files applied to external dependencies managed by
Bazel's module system (Bzlmod) in the main `MODULE.bazel` file.

These patches are necessary to:

1.  **Adapt dependencies to the Bazel module system:** Some upstream projects
    are not yet fully compatible with Bazel modules. Patches may add or modify
    `MODULE.bazel` files, adjust repository names, or update load statements.
2.  **Ensure compatibility:** Modify dependencies to work correctly within the
    `deviceinfra` project, such as updating dependency labels (e.g.,
    `@io_bazel_rules_go` to `@rules_go`) or adjusting build targets.
3.  **Control code generation:** For example, the `googleapis.patch` allows the
    `deviceinfra` module to control which programming language bindings are
    generated for the Google APIs.

## Patch Files:

*   **`bazelbuild_remote_apis_sdks.patch`**:
    *   Adjusts dependency labels for gRPC and Google API dependencies.
    *   Updates references from `@io_bazel_rules_go` to `@rules_go`.
*   **`googleapis.patch`**:
    *   Adds a `MODULE.bazel` file to turn the repo into a Bazel module.
    *   Defines a module extension (`switched_rules`) to control language
        binding generation.
    *   Updates references from `@io_bazel_rules_go` to `@rules_go`.
