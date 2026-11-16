import {
  getStoredGroupExpandPageSize,
  getStoredPageSize,
  saveStoredGroupExpandPageSize,
  saveStoredPageSize,
} from './pagination_utils';

describe('pagination_utils', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  describe('flat page size storage', () => {
    it('returns null when nothing is stored', () => {
      expect(getStoredPageSize('devices')).toBeNull();
    });

    it('saves and reads back the page size under a per-entity key', () => {
      saveStoredPageSize('devices', 5000);

      expect(getStoredPageSize('devices')).toBe(5000);
      expect(window.localStorage.getItem('mh_fe_v6_page_size_devices')).toBe(
        '5000',
      );
      expect(getStoredPageSize('hosts')).toBeNull();
    });

    it('keeps device and host page sizes independent', () => {
      saveStoredPageSize('devices', 500);
      saveStoredPageSize('hosts', 1000);

      expect(getStoredPageSize('devices')).toBe(500);
      expect(getStoredPageSize('hosts')).toBe(1000);
    });

    it('ignores stored values that are not an offered option', () => {
      window.localStorage.setItem('mh_fe_v6_page_size_devices', '123');
      expect(getStoredPageSize('devices')).toBeNull();

      window.localStorage.setItem('mh_fe_v6_page_size_devices', '"5000"');
      expect(getStoredPageSize('devices')).toBeNull();

      window.localStorage.setItem('mh_fe_v6_page_size_devices', 'not json');
      expect(getStoredPageSize('devices')).toBeNull();
    });
  });

  describe('group expand page size storage', () => {
    it('returns null when nothing is stored', () => {
      expect(getStoredGroupExpandPageSize('devices')).toBeNull();
    });

    it('saves and reads back the page size under a separate per-entity key', () => {
      saveStoredGroupExpandPageSize('devices', 2000);

      expect(getStoredGroupExpandPageSize('devices')).toBe(2000);
      expect(
        window.localStorage.getItem('mh_fe_v6_group_expand_page_size_devices'),
      ).toBe('2000');
      // The flat and group-expand preferences never share a key.
      expect(getStoredPageSize('devices')).toBeNull();
      expect(getStoredGroupExpandPageSize('hosts')).toBeNull();
    });

    it('ignores stored values that are not an offered option', () => {
      window.localStorage.setItem(
        'mh_fe_v6_group_expand_page_size_hosts',
        '99999',
      );
      expect(getStoredGroupExpandPageSize('hosts')).toBeNull();
    });
  });
});
