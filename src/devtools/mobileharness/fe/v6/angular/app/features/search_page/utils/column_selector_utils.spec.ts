import {
  clearStoredVisibleColumns,
  getStoredVisibleColumns,
  getUnknownColumnKeyFromError,
  saveStoredVisibleColumns,
} from './column_selector_utils';

describe('column_selector_utils', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  describe('visible columns persistence', () => {
    it('returns null when no stored visible columns exist', () => {
      expect(getStoredVisibleColumns('devices', 'internal')).toBeNull();
    });

    it('saves and retrieves stored visible columns as FleetColumnDescriptor[] per entity and fleet', () => {
      saveStoredVisibleColumns('devices', 'internal', [
        {key: 'field::uuid', displayName: 'Device ID', locked: true},
        {key: 'field::status', displayName: 'Status'},
        {key: 'dim::model', displayName: 'Model'},
      ]);
      expect(getStoredVisibleColumns('devices', 'internal')).toEqual([
        {key: 'field::uuid', displayName: 'Device ID', locked: true},
        {key: 'field::status', displayName: 'Status'},
        {key: 'dim::model', displayName: 'Model'},
      ]);
      expect(getStoredVisibleColumns('hosts', 'internal')).toBeNull();
      expect(getStoredVisibleColumns('devices', 'ats')).toBeNull();
    });

    it('clears stored visible columns from localStorage', () => {
      saveStoredVisibleColumns('devices', 'internal', [
        {key: 'field::uuid', displayName: 'Device ID', locked: true},
      ]);
      expect(getStoredVisibleColumns('devices', 'internal')).not.toBeNull();
      clearStoredVisibleColumns('devices', 'internal');
      expect(getStoredVisibleColumns('devices', 'internal')).toBeNull();
    });

    it('returns null when stored visible columns value in localStorage is corrupted JSON', () => {
      window.localStorage.setItem(
        'mh_fe_v6_visible_columns_devices_internal',
        'invalid-json-{',
      );
      expect(getStoredVisibleColumns('devices', 'internal')).toBeNull();
    });
  });

  describe('getUnknownColumnKeyFromError', () => {
    it('extracts the key from an OSS (Envoy) "Unknown device key" error body', () => {
      expect(
        getUnknownColumnKeyFromError({
          status: 500,
          error: {code: 13, message: 'Unknown device key: field::uuid', details: []},
        }),
      ).toBe('field::uuid');
    });

    it('extracts the key from an internal (ESF) "Unknown host key" error body', () => {
      expect(
        getUnknownColumnKeyFromError({
          status: 500,
          error: {error: {code: 500, message: 'Unknown host key: field::host_name'}},
        }),
      ).toBe('field::host_name');
    });

    it('returns null for other errors', () => {
      expect(
        getUnknownColumnKeyFromError({
          status: 500,
          error: {code: 13, message: 'Unknown filter key: foo'},
        }),
      ).toBeNull();
      expect(getUnknownColumnKeyFromError(new Error('network down'))).toBeNull();
      expect(getUnknownColumnKeyFromError(null)).toBeNull();
    });
  });
});
