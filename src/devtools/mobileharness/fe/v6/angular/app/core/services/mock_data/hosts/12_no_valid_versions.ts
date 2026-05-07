/** @fileoverview Mock host scenario with no valid versions for release. */

import {MockHostScenario} from '../models';
import {SCENARIO_HOST_BASIC_EDITABLE} from './02_basic_editable';
import {createDefaultHostOverview} from './ui_status_utils';

/**
 * Represents a mock host scenario where there are no valid versions for release.
 */
export const SCENARIO_HOST_NO_VALID_VERSIONS: MockHostScenario = {
  ...SCENARIO_HOST_BASIC_EDITABLE,
  hostName: 'no-valid-versions.host.example.com',
  scenarioName: '12. No Valid Versions',
  overview: createDefaultHostOverview('no-valid-versions.host.example.com'),
  deviceSummaries: [],
  releaseResponse: {
    ready: {
      versions: [],
    },
  },
};
