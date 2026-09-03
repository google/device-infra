import {
  Fleet,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';
import {buildDrilldownUrl} from './drilldown_utils';

describe('drilldown_utils', () => {
  describe('buildDrilldownUrl', () => {
    it('routes host search target to /hosts and device target to /devices', () => {
      const hostTarget = {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      };
      const deviceTarget = {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      };

      expect(buildDrilldownUrl(hostTarget).path).toBe('/hosts');
      expect(buildDrilldownUrl(deviceTarget).path).toBe('/devices');
    });

    it('sets fleet: ats when fleet is FLEET_ATS and fleet: null when not', () => {
      const atsTarget = {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_ATS,
        filters: [],
      };
      const fpTarget = {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      };

      expect(buildDrilldownUrl(atsTarget).queryParams['fleet']).toBe('ats');
      expect(buildDrilldownUrl(fpTarget).queryParams['fleet']).toBeNull();
    });

    it('sets f: null when no filters are provided', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toBeNull();
      expect(result.queryParams['gb']).toBeNull();
    });

    it('handles undefined filters gracefully', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toBeNull();
      expect(result.queryParams['gb']).toBeNull();
    });

    it('encodes simple filters with values correctly', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [
          {
            key: 'field::status',
            simple: {values: [{value: 'BUSY'}, {value: 'IDLE'}]},
          },
        ],
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toEqual(['field::status~BUSY,IDLE']);
    });

    it('encodes negated simple filters with exclamation mark prefix', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [
          {
            key: 'field::status',
            simple: {
              values: [{value: 'BUSY'}, {value: 'IDLE'}],
              negated: true,
            },
          },
        ],
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toEqual(['!field::status~BUSY,IDLE']);
    });

    it('encodes simple filter without values as bare key', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: [
          {
            key: 'has::quarantine',
            simple: {values: []},
          },
        ],
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toEqual(['has::quarantine']);
    });

    it('encodes non-simple filter as bare key', () => {
      const target = {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: [
          {
            key: 'field::created_time',
          },
        ],
      };

      const result = buildDrilldownUrl(target);
      expect(result.queryParams['f']).toEqual(['field::created_time']);
    });
  });
});
