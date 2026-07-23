import {MockHostScenario} from '../models';
import {OVERVIEW_01} from './overview_01';

/** Scenario for testing host refresh/failure retention logic. */
export const OVERVIEW_REFRESH: MockHostScenario = {
  ...OVERVIEW_01,
  scenarioName: 'Host Refresh Test Scenario',
  hostName: 'refresh-host-name',
  overview: OVERVIEW_01.overview
    ? {
        ...OVERVIEW_01.overview,
        hostName: 'refresh-host-name',
      }
    : undefined,
};
