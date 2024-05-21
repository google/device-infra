# proto-file: third_party/deviceinfra/src/devtools/mobileharness/infra/ats/console/tradefed/proto/test_record.proto
# proto-message: android_test_record.TestRecord

children {
  inline_test_record {
    test_record_id: "arm64-v8a Module1"
    parent_test_record_id: "dummy_parent_id"
    children {
      inline_test_record {
        test_record_id: "arm64-v8a Module1"
        parent_test_record_id: "arm64-v8a Module1"
        children {
          inline_test_record {
            test_record_id: "dummy_test_1"
            parent_test_record_id: "arm64-v8a Module1"
          }
        }
        children {
          inline_test_record {
            test_record_id: "dummy_test_2"
            parent_test_record_id: "arm64-v8a Module1"
          }
        }
        metrics {
          key: "PREP_TIME"
          value {
            measurements {
              single_int: 123456
            }
          }
        }
        metrics {
          key: "TEARDOWN_TIME"
          value {
            measurements {
              single_int: 654321
            }
          }
        }
      }
    }
  }
}
children {
  inline_test_record {
    test_record_id: "arm64-v8a Module2"
    parent_test_record_id: "dummy_parent_id"
    children {
      inline_test_record {
        test_record_id: "arm64-v8a Module2"
        parent_test_record_id: "arm64-v8a Module2"
        children {
          inline_test_record {
            test_record_id: "hello_test_1"
            parent_test_record_id: "arm64-v8a Module2"
          }
        }
        children {
          inline_test_record {
            test_record_id: "hello_test_2"
            parent_test_record_id: "arm64-v8a Module2"
          }
        }
        metrics {
          key: "PREP_TIME"
          value {
            measurements {
              single_int: 321
            }
          }
        }
        metrics {
          key: "TEARDOWN_TIME"
          value {
            measurements {
              single_int: 123
            }
          }
        }
      }
    }
  }
}
