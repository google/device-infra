import {
  Indicator,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionResponse,
} from '@deviceinfra/app/core/models/search';

/** Mock test search config. */
export const MOCK_TJS_SEARCH_CONFIG_TEST: TjsSearchConfig = {
  entityLabel: 'Tests',
  promotedKeys: [
    {
      key: 'result',
      displayName: 'Result',
      enumPicker: {
        options: [
          {value: 'PASS', label: 'Pass'},
          {value: 'FAIL', label: 'Fail'},
          {value: 'ERROR', label: 'Error'},
          {value: 'TIMEOUT', label: 'Timeout'},
          {value: 'SKIP', label: 'Skip'},
        ],
        multiSelect: true,
      },
    },
    {key: 'name', displayName: 'Test Name', textInput: {}},
    {key: 'user', displayName: 'User', textInput: {}},
    {key: 'device_id', displayName: 'Device ID', textInput: {}},
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
};

/** Mock job search config. */
export const MOCK_TJS_SEARCH_CONFIG_JOB: TjsSearchConfig = {
  entityLabel: 'Jobs',
  promotedKeys: [
    {
      key: 'status',
      displayName: 'Status',
      enumPicker: {
        options: [
          {value: 'RUNNING', label: 'Running'},
          {value: 'DONE', label: 'Done'},
          {value: 'KILLED', label: 'Killed'},
        ],
        multiSelect: true,
      },
    },
    {key: 'name', displayName: 'Job Name', textInput: {}},
    {key: 'user', displayName: 'User', textInput: {}},
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
  defaultChips: [
    {
      pillKey: 'Result',
      pillCondition: 'PASS, FAIL',
      keyDisplayName: 'Result',
      filter: {
        key: 'result',
        enumValues: {values: ['PASS', 'FAIL']},
      },
    },
  ],
};

/** Mock session search config. */
export const MOCK_TJS_SEARCH_CONFIG_SESSION: TjsSearchConfig = {
  entityLabel: 'Sessions',
  promotedKeys: [
    {
      key: 'status',
      displayName: 'Status',
      enumPicker: {
        options: [
          {value: 'RUNNING', label: 'Running'},
          {value: 'DONE', label: 'Done'},
        ],
      },
    },
    {key: 'user', displayName: 'User', textInput: {}},
  ],
};

/** Mock test search response. */
export const MOCK_TJS_SEARCH_RESPONSE_TEST: TjsSearchResponse = {
  columns: [
    {key: 'test_id', displayName: 'Test ID', kind: 'KIND_ID_LINK'},
    {key: 'name', displayName: 'Test name', kind: 'KIND_TEXT'},
    {key: 'user', displayName: 'User', kind: 'KIND_TEXT'},
    {key: 'actual_user', displayName: 'Actual user', kind: 'KIND_TEXT'},
    {key: 'status', displayName: 'Status', kind: 'KIND_TEXT'},
    {key: 'result', displayName: 'Result', kind: 'KIND_TEXT'},
    {key: 'start_time', displayName: 'Start time', kind: 'KIND_TIME'},
    {key: 'duration', displayName: 'Duration', kind: 'KIND_DURATION'},
    {key: 'host_name', displayName: 'Lab (host)', kind: 'KIND_TEXT'},
    {key: 'devices', displayName: 'Devices', kind: 'KIND_LIST'},
  ],
  rows: [
    {
      id: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
      cells: [
        {
          value: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
          kind: 'KIND_ID_LINK',
          navTarget: {
            targetType: 'NAV_TARGET_TEST_DETAIL',
            targetId: 'baf01a94-f625-4d65-9f3d-938d30e5f6f8',
          },
        },
        {
          value:
            'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#addStatsToSponge',
          kind: 'KIND_TEXT',
        },
        {value: 'qiupingf', kind: 'KIND_TEXT'},
        {value: 'qiupingf', kind: 'KIND_TEXT'},
        {
          value: 'DONE',
          status: {text: 'DONE', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {
          value: 'PASS',
          status: {text: 'PASS', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {value: '1785826199655', kind: 'KIND_TIME'},
        {value: '32358', kind: 'KIND_DURATION'},
        {value: 'mt08-dm02.atl14.mtaas.google.com', kind: 'KIND_TEXT'},
        {value: '18261FDF6003KC', kind: 'KIND_LIST'},
      ],
    },
    {
      id: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
      cells: [
        {
          value: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
          kind: 'KIND_ID_LINK',
          navTarget: {
            targetType: 'NAV_TARGET_TEST_DETAIL',
            targetId: 'b6a4e952-5a5c-4728-875f-322bfce27bc8',
          },
        },
        {
          value:
            'com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest#plusOneButton',
          kind: 'KIND_TEXT',
        },
        {value: 'qiupingf', kind: 'KIND_TEXT'},
        {value: 'qiupingf', kind: 'KIND_TEXT'},
        {
          value: 'DONE',
          status: {text: 'DONE', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {
          value: 'PASS',
          status: {text: 'PASS', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {value: '1785826199666', kind: 'KIND_TIME'},
        {value: '49537', kind: 'KIND_DURATION'},
        {value: 'mt208-dm01.atl14.mtaas.google.com', kind: 'KIND_TEXT'},
        {value: '18271FDF600EZ0', kind: 'KIND_LIST'},
      ],
    },
  ],
};

/** Mock job search response. */
export const MOCK_TJS_SEARCH_RESPONSE_JOB: TjsSearchResponse = {
  columns: [
    {key: 'job_id', displayName: 'Job ID', kind: 'KIND_ID_LINK'},
    {key: 'name', displayName: 'Job name', kind: 'KIND_TEXT'},
    {key: 'user', displayName: 'User', kind: 'KIND_TEXT'},
    {key: 'status', displayName: 'Status', kind: 'KIND_TEXT'},
    {key: 'start_time', displayName: 'Start time', kind: 'KIND_TIME'},
    {key: 'duration', displayName: 'Duration', kind: 'KIND_DURATION'},
  ],
  rows: [
    {
      id: 'job_1',
      cells: [
        {
          link: {text: 'j_987654', target: {job: {jobId: 'j_987654'}}},
          kind: 'KIND_ID_LINK',
        },
        {text: {value: 'MobileHarness Core Tests'}, kind: 'KIND_TEXT'},
        {text: {value: 'qiupingf'}, kind: 'KIND_TEXT'},
        {
          status: {text: 'RUNNING', indicator: Indicator.INDICATOR_ACTIVE},
          kind: 'KIND_TEXT',
        },
        {value: '1723550400000', kind: 'KIND_TIME'},
        {value: '124500', kind: 'KIND_DURATION'},
      ],
    },
    {
      id: 'job_2',
      cells: [
        {
          link: {text: 'j_876543', target: {job: {jobId: 'j_876543'}}},
          kind: 'KIND_ID_LINK',
        },
        {text: {value: 'Device Infra Integration'}, kind: 'KIND_TEXT'},
        {text: {value: 'dev_user'}, kind: 'KIND_TEXT'},
        {
          status: {text: 'DONE', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {value: '1723546800000', kind: 'KIND_TIME'},
        {value: '600000', kind: 'KIND_DURATION'},
      ],
    },
  ],
};

/** Mock session search response. */
export const MOCK_TJS_SEARCH_RESPONSE_SESSION: TjsSearchResponse = {
  columns: [
    {key: 'session_id', displayName: 'Session ID', kind: 'KIND_ID_LINK'},
    {key: 'user', displayName: 'User', kind: 'KIND_TEXT'},
    {key: 'status', displayName: 'Status', kind: 'KIND_TEXT'},
    {key: 'start_time', displayName: 'Start time', kind: 'KIND_TIME'},
    {key: 'duration', displayName: 'Duration', kind: 'KIND_DURATION'},
  ],
  rows: [
    {
      id: 'session_1',
      cells: [
        {
          link: {text: 's_abcdef', target: {session: {sessionId: 's_abcdef'}}},
          kind: 'KIND_ID_LINK',
        },
        {text: {value: 'qiupingf'}, kind: 'KIND_TEXT'},
        {
          status: {text: 'DONE', indicator: Indicator.INDICATOR_OK},
          kind: 'KIND_TEXT',
        },
        {value: '1723550400000', kind: 'KIND_TIME'},
        {value: '180000', kind: 'KIND_DURATION'},
      ],
    },
    {
      id: 'session_2',
      cells: [
        {
          link: {text: 's_123456', target: {session: {sessionId: 's_123456'}}},
          kind: 'KIND_ID_LINK',
        },
        {text: {value: 'dev_user'}, kind: 'KIND_TEXT'},
        {
          status: {text: 'RUNNING', indicator: Indicator.INDICATOR_ACTIVE},
          kind: 'KIND_TEXT',
        },
        {value: '1723554000000', kind: 'KIND_TIME'},
        {value: '90000', kind: 'KIND_DURATION'},
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
