import {
  Fleet,
  FleetSearchTarget,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';
import {DrilldownRoutePipe} from './drilldown_pipe';

describe('DrilldownRoutePipe', () => {
  let pipe: DrilldownRoutePipe;

  beforeEach(() => {
    pipe = new DrilldownRoutePipe();
  });

  it('returns null when target is undefined or null', () => {
    expect(pipe.transform(undefined)).toBeNull();
    expect(pipe.transform(null)).toBeNull();
  });

  it('builds drilldown route and query parameters for valid target', () => {
    const target: FleetSearchTarget = {
      entity: SearchEntity.SEARCH_ENTITY_DEVICE,
      fleet: Fleet.FLEET_SELF,
      filters: [{key: 'field::status', simple: {values: [{value: 'BUSY'}]}}],
    };

    const result = pipe.transform(target);
    expect(result).toEqual({
      path: '/devices',
      queryParams: {
        f: ['field::status~BUSY'],
        gb: null,
        fleet: null,
      },
    });
  });
});
