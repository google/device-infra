import {
  clearStoredVisibleColumns,
  getStoredVisibleColumns,
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
});
