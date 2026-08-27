import {
  Indicator,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionResponse,
} from '../../../../core/models/search';

/** Mock page-load configuration for test search page. */
export const MOCK_TJS_SEARCH_CONFIG_TEST: TjsSearchConfig = {
  entityLabel: 'Tests',
  defaultChips: [
    {
      pillKey: 'Result',
      pillCondition: 'PASS',
      keyDisplayName: 'Result',
      filter: {
        key: 'result',
        enumValues: {values: ['PASS']},
      },
    },
  ],
  promotedKeys: [
    {
      key: 'test_id',
      displayName: 'Test ID',
      textInput: {placeholder: 'Full test id'},
    },
    {
      key: 'job_id',
      displayName: 'Job ID',
      textInput: {placeholder: 'Full job id'},
    },
    {
      key: 'name',
      displayName: 'Name',
      textInput: {placeholder: 'Test name contains…'},
    },
    {
      key: 'user',
      displayName: 'User',
      textInput: {placeholder: 'Exact username'},
    },
    {
      key: 'device_id',
      displayName: 'Device ID',
      textInput: {placeholder: 'Exact device id'},
    },
    {
      key: 'host_name',
      displayName: 'Host name',
      textInput: {placeholder: 'Host name contains…'},
    },
    {
      key: 'status',
      displayName: 'Status',
      enumPicker: {
        options: [
          {value: 'NEW', label: 'NEW'},
          {value: 'ASSIGNED', label: 'ASSIGNED'},
          {value: 'RUNNING', label: 'RUNNING'},
          {value: 'DONE', label: 'DONE'},
          {value: 'SUSPENDED', label: 'SUSPENDED'},
        ],
        multiSelect: true,
      },
    },
    {
      key: 'result',
      displayName: 'Result',
      enumPicker: {
        options: [
          {value: 'PASS', label: 'PASS'},
          {value: 'FAIL', label: 'FAIL'},
          {value: 'ERROR', label: 'ERROR'},
          {value: 'TIMEOUT', label: 'TIMEOUT'},
          {value: 'ABORT', label: 'ABORT'},
          {value: 'SKIP', label: 'SKIP'},
          {value: 'UNKNOWN', label: 'UNKNOWN'},
        ],
        multiSelect: true,
      },
    },
    {
      key: 'property',
      displayName: 'Actual user',
      textInput: {placeholder: 'Exact actual user'},
    },
    {
      key: 'property',
      displayName: 'Property',
      namedPair: {
        namePlaceholder: 'Property name',
        valuePlaceholder: 'Property value',
      },
    },
    {
      key: 'create_time',
      displayName: 'Create Time',
      timeRange: {},
    },
  ],
};

/** Mock page-load configuration for job search page. */
export const MOCK_TJS_SEARCH_CONFIG_JOB: TjsSearchConfig = {
  entityLabel: 'Jobs',
  defaultChips: [
    {
      pillKey: 'Status',
      pillCondition: 'RUNNING',
      keyDisplayName: 'Status',
      filter: {
        key: 'status',
        enumValues: {values: ['RUNNING']},
      },
    },
  ],
  promotedKeys: [
    {
      key: 'id',
      displayName: 'Job ID',
      textInput: {placeholder: 'Full job id'},
    },
    {
      key: 'name',
      displayName: 'Name',
      textInput: {placeholder: 'Job name contains…'},
    },
    {
      key: 'user',
      displayName: 'User',
      textInput: {placeholder: 'Exact username'},
    },
    {
      key: 'device',
      displayName: 'Device',
      textInput: {placeholder: 'Device type'},
    },
    {
      key: 'driver',
      displayName: 'Driver',
      textInput: {placeholder: 'Driver'},
    },
    {
      key: 'decorator',
      displayName: 'Decorator',
      textInput: {placeholder: 'Decorator'},
    },
    {
      key: 'status',
      displayName: 'Status',
      enumPicker: {
        options: [
          {value: 'NEW', label: 'NEW'},
          {value: 'ASSIGNED', label: 'ASSIGNED'},
          {value: 'RUNNING', label: 'RUNNING'},
          {value: 'DONE', label: 'DONE'},
          {value: 'SUSPENDED', label: 'SUSPENDED'},
        ],
        multiSelect: true,
      },
    },
    {
      key: 'result',
      displayName: 'Result',
      enumPicker: {
        options: [
          {value: 'PASS', label: 'PASS'},
          {value: 'FAIL', label: 'FAIL'},
          {value: 'ERROR', label: 'ERROR'},
          {value: 'TIMEOUT', label: 'TIMEOUT'},
          {value: 'ABORT', label: 'ABORT'},
          {value: 'SKIP', label: 'SKIP'},
          {value: 'UNKNOWN', label: 'UNKNOWN'},
        ],
        multiSelect: true,
      },
    },
    {
      key: 'dimension',
      displayName: 'Dimension',
      namedPair: {
        namePlaceholder: 'Dimension name',
        valuePlaceholder: 'Value',
      },
    },
    {
      key: 'property',
      displayName: 'Actual user',
      textInput: {placeholder: 'Exact actual user'},
    },
    {
      key: 'property',
      displayName: 'Property',
      namedPair: {
        namePlaceholder: 'Property name',
        valuePlaceholder: 'Property value',
      },
    },
    {
      key: 'create_time',
      displayName: 'Create Time',
      timeRange: {},
    },
  ],
};

/** Mock page-load configuration for session search page. */
export const MOCK_TJS_SEARCH_CONFIG_SESSION: TjsSearchConfig = {
  entityLabel: 'Sessions',
  defaultChips: [
    {
      pillKey: 'User',
      pillCondition: 'qiupingf',
      keyDisplayName: 'User',
      filter: {
        key: 'user',
        stringValue: {value: 'qiupingf'},
      },
    },
  ],
  promotedKeys: [
    {
      key: 'id',
      displayName: 'Session ID',
      textInput: {placeholder: 'Full session id'},
    },
    {
      key: 'user',
      displayName: 'User',
      textInput: {placeholder: 'Exact username'},
    },
    {
      key: 'client',
      displayName: 'Client',
      textInput: {placeholder: 'Exact client'},
    },
    {
      key: 'label',
      displayName: 'Label',
      textInput: {placeholder: 'Exact label'},
    },
  ],
};

/** Mock test search response (100% compliant with search_common.proto). */
export const MOCK_TJS_SEARCH_RESPONSE_TEST: TjsSearchResponse = {
  columns: [
    {key: 'test_id', displayName: 'Test ID'},
    {key: 'name', displayName: 'Test name'},
    {key: 'user', displayName: 'User'},
    {key: 'actual_user', displayName: 'Actual user'},
    {key: 'status', displayName: 'Status'},
    {key: 'result', displayName: 'Result'},
    {key: 'start_time', displayName: 'Start time'},
    {key: 'duration', displayName: 'Duration'},
    {key: 'host_name', displayName: 'Lab (host)'},
    {key: 'devices', displayName: 'Devices'},
  ],
  rows: [
    {
      id: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
      cells: [
        {
          link: {
            text: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
            target: {
              test: {
                testId: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
                jobId: 'j_987654',
              },
            },
          },
        },
        {
          text: {
            value:
              'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#addStatsToSponge',
          },
        },
        {text: {value: 'qiupingf'}},
        {text: {value: 'qiupingf'}},
        {status: {text: 'DONE', indicator: Indicator.INDICATOR_OK}},
        {status: {text: 'PASS', indicator: Indicator.INDICATOR_OK}},
        {text: {value: '1785826199655'}},
        {text: {value: '32358'}},
        {text: {value: 'mt08-dm02.atl14.mtaas.google.com'}},
        {
          multiLink: {
            entries: [
              {
                text: '18261FDF6003KC',
                target: {device: {id: '18261FDF6003KC'}},
              },
              {
                text: '98888FDF6005AB',
                target: {device: {id: '98888FDF6005AB'}},
              },
            ],
          },
        },
      ],
    },
    {
      id: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
      cells: [
        {
          link: {
            text: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
            target: {
              test: {
                testId: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
                jobId: 'j_987654',
              },
            },
          },
        },
        {
          text: {
            value:
              'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#plusOneButton',
          },
        },
        {text: {value: 'qiupingf'}},
        {text: {value: 'qiupingf'}},
        {status: {text: 'DONE', indicator: Indicator.INDICATOR_OK}},
        {status: {text: 'PASS', indicator: Indicator.INDICATOR_OK}},
        {text: {value: '1785826199666'}},
        {text: {value: '49537'}},
        {text: {value: 'mt208-dm01.atl14.mtaas.google.com'}},
        {chips: {values: ['18271FDF600EZ0']}},
      ],
    },
  ],
};

/** Mock job search response (100% compliant with search_common.proto). */
export const MOCK_TJS_SEARCH_RESPONSE_JOB: TjsSearchResponse = {
  columns: [
    {key: 'job_id', displayName: 'Job ID'},
    {key: 'name', displayName: 'Job name'},
    {key: 'user', displayName: 'User'},
    {key: 'status', displayName: 'Status'},
    {key: 'start_time', displayName: 'Start time'},
    {key: 'duration', displayName: 'Duration'},
  ],
  rows: [
    {
      id: 'job_1',
      cells: [
        {link: {text: 'j_987654', target: {job: {jobId: 'j_987654'}}}},
        {text: {value: 'MobileHarness Core Tests'}},
        {text: {value: 'qiupingf'}},
        {status: {text: 'RUNNING', indicator: Indicator.INDICATOR_ACTIVE}},
        {text: {value: '1723550400000'}},
        {text: {value: '124500'}},
      ],
    },
    {
      id: 'job_2',
      cells: [
        {link: {text: 'j_876543', target: {job: {jobId: 'j_876543'}}}},
        {text: {value: 'Device Infra Integration'}},
        {text: {value: 'dev_user'}},
        {status: {text: 'DONE', indicator: Indicator.INDICATOR_OK}},
        {text: {value: '1723546800000'}},
        {text: {value: '600000'}},
      ],
    },
  ],
};

/** Mock session search response (100% compliant with search_common.proto). */
export const MOCK_TJS_SEARCH_RESPONSE_SESSION: TjsSearchResponse = {
  columns: [
    {key: 'session_id', displayName: 'Session ID'},
    {key: 'user', displayName: 'User'},
    {key: 'status', displayName: 'Status'},
    {key: 'start_time', displayName: 'Start time'},
    {key: 'duration', displayName: 'Duration'},
  ],
  rows: [
    {
      id: 'session_1',
      cells: [
        {link: {text: 's_abcdef', target: {session: {sessionId: 's_abcdef'}}}},
        {text: {value: 'qiupingf'}},
        {status: {text: 'DONE', indicator: Indicator.INDICATOR_OK}},
        {text: {value: '1723550400000'}},
        {text: {value: '180000'}},
      ],
    },
    {
      id: 'session_2',
      cells: [
        {link: {text: 's_123456', target: {session: {sessionId: 's_123456'}}}},
        {text: {value: 'dev_user'}},
        {status: {text: 'RUNNING', indicator: Indicator.INDICATOR_ACTIVE}},
        {text: {value: '1723554000000'}},
        {text: {value: '90000'}},
      ],
    },
  ],
};

/** Mock default suggestions for test search. */
export const MOCK_TJS_SUGGESTIONS_TEST_DEFAULT: TjsSuggestion[] = [
  {
    label: 'Add filter',
    mainText: [
      {text: 'Result is ', emphasized: false},
      {text: 'PASS', emphasized: true},
    ],
    applyFilter: {
      pillKey: 'Result',
      pillCondition: 'PASS',
      keyDisplayName: 'Result',
      filter: {
        key: 'result',
        enumValues: {values: ['PASS']},
      },
    },
  },
];

/** Mock suggestions list for job search. */
export const MOCK_TJS_SUGGESTIONS_JOB: TjsSuggestion[] = [
  {
    label: 'Add filter',
    mainText: [
      {text: 'Status is ', emphasized: false},
      {text: 'RUNNING', emphasized: true},
    ],
    applyFilter: {
      pillKey: 'Status',
      pillCondition: 'RUNNING',
      keyDisplayName: 'Status',
      filter: {
        key: 'status',
        enumValues: {values: ['RUNNING']},
      },
    },
  },
];

/** Mock suggestions list for session search. */
export const MOCK_TJS_SUGGESTIONS_SESSION: TjsSuggestion[] = [
  {
    label: 'Add filter',
    mainText: [
      {text: 'User is ', emphasized: false},
      {text: 'qiupingf', emphasized: true},
    ],
    applyFilter: {
      pillKey: 'User',
      pillCondition: 'qiupingf',
      keyDisplayName: 'User',
      filter: {
        key: 'user',
        stringValue: {value: 'qiupingf'},
      },
    },
  },
];

/** Mock suggestion response for test search. */
export const MOCK_TJS_SUGGESTION_RESPONSE_TEST: TjsSuggestionResponse = {
  items: MOCK_TJS_SUGGESTIONS_TEST_DEFAULT,
};

/** Mock suggestion response for job search. */
export const MOCK_TJS_SUGGESTION_RESPONSE_JOB: TjsSuggestionResponse = {
  items: MOCK_TJS_SUGGESTIONS_JOB,
};

/** Mock suggestion response for session search. */
export const MOCK_TJS_SUGGESTION_RESPONSE_SESSION: TjsSuggestionResponse = {
  items: MOCK_TJS_SUGGESTIONS_SESSION,
};

/** Mock resolve chips response. */
export const MOCK_TJS_RESOLVE_CHIPS_RESPONSE: TjsResolveChipsResponse = {
  chips: [
    {
      pillKey: 'Result',
      pillCondition: 'PASS',
      keyDisplayName: 'Result',
    },
    {
      pillKey: 'Status',
      pillCondition: 'RUNNING',
      keyDisplayName: 'Status',
    },
    {
      pillKey: 'User',
      pillCondition: 'qiupingf',
      keyDisplayName: 'User',
    },
  ],
};
