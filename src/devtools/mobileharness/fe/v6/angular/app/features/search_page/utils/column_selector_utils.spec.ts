import {
  clearStoredVisibleColumns,
  getStoredVisibleColumns,
  saveStoredVisibleColumns,
} from './column_selector_utils';

describe('column_selector_utils', () => {
  describe('localStorage persistence helpers', () => {
    beforeEach(() => {
      window.localStorage.clear();
    });

    it('returns null when no stored visible columns exist', () => {
      expect(getStoredVisibleColumns('devices', 'internal')).toBeNull();
    });

    it('saves and retrieves stored visible columns as FleetColumnDescriptor[] per entity and fleet', () => {
      saveStoredVisibleColumns('devices', 'internal', [
        {key: 'id', displayName: 'Device ID', locked: true},
        {key: 'status', displayName: 'Status'},
        {key: 'model', displayName: 'Model'},
      ]);
      expect(getStoredVisibleColumns('devices', 'internal')).toEqual([
        {key: 'id', displayName: 'Device ID', locked: true},
        {key: 'status', displayName: 'Status'},
        {key: 'model', displayName: 'Model'},
      ]);
      expect(getStoredVisibleColumns('hosts', 'internal')).toBeNull();
      expect(getStoredVisibleColumns('devices', 'ats')).toBeNull();
    });

    it('clears stored visible columns from localStorage', () => {
      saveStoredVisibleColumns('devices', 'internal', [
        {key: 'id', displayName: 'Device ID', locked: true},
      ]);
      expect(getStoredVisibleColumns('devices', 'internal')).not.toBeNull();
      clearStoredVisibleColumns('devices', 'internal');
      expect(getStoredVisibleColumns('devices', 'internal')).toBeNull();
    });
  });
});
