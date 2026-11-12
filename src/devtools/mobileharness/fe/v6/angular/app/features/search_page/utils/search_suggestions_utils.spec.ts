import {
  clearStoredAutoCollapseAfterApply,
  getStoredAutoCollapseAfterApply,
  getStoredFilterCollapsed,
  saveStoredAutoCollapseAfterApply,
  saveStoredFilterCollapsed,
} from './search_suggestions_utils';

describe('search_suggestions_utils', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  describe('auto-collapse after apply persistence', () => {
    it('returns null when no stored auto-collapse preference exists', () => {
      expect(getStoredAutoCollapseAfterApply('devices', 'internal')).toBeNull();
    });

    it('saves and retrieves stored auto-collapse preference per entity and fleet', () => {
      saveStoredAutoCollapseAfterApply('devices', 'internal', false);
      expect(
        getStoredAutoCollapseAfterApply('devices', 'internal'),
      ).toBeFalse();
      expect(getStoredAutoCollapseAfterApply('hosts', 'internal')).toBeNull();
      expect(getStoredAutoCollapseAfterApply('devices', 'ats')).toBeNull();

      saveStoredAutoCollapseAfterApply('devices', 'internal', true);
      expect(getStoredAutoCollapseAfterApply('devices', 'internal')).toBeTrue();
    });

    it('clears stored auto-collapse preference from localStorage', () => {
      saveStoredAutoCollapseAfterApply('devices', 'internal', false);
      expect(
        getStoredAutoCollapseAfterApply('devices', 'internal'),
      ).toBeFalse();
      clearStoredAutoCollapseAfterApply('devices', 'internal');
      expect(getStoredAutoCollapseAfterApply('devices', 'internal')).toBeNull();
    });

    it('returns null when stored auto-collapse value in localStorage is corrupted JSON', () => {
      window.localStorage.setItem(
        'mh_fe_v6_auto_collapse_after_apply_devices_internal',
        'invalid-json-{',
      );
      expect(getStoredAutoCollapseAfterApply('devices', 'internal')).toBeNull();
    });
  });

  describe('quick filter panel collapsed persistence', () => {
    it('returns null when no stored collapsed state exists', () => {
      expect(getStoredFilterCollapsed('devices')).toBeNull();
    });

    it('saves and retrieves the collapsed state separately per entity', () => {
      saveStoredFilterCollapsed('devices', true);
      expect(getStoredFilterCollapsed('devices')).toBeTrue();
      expect(getStoredFilterCollapsed('hosts')).toBeNull();

      saveStoredFilterCollapsed('hosts', false);
      expect(getStoredFilterCollapsed('hosts')).toBeFalse();
      expect(getStoredFilterCollapsed('devices')).toBeTrue();
      expect(
        window.localStorage.getItem('mh_fe_v6_filter_collapsed_devices'),
      ).toBe('true');
    });

    it('returns null when the stored collapsed value is not a boolean', () => {
      window.localStorage.setItem(
        'mh_fe_v6_filter_collapsed_devices',
        '"yes"',
      );
      expect(getStoredFilterCollapsed('devices')).toBeNull();
    });
  });
});
