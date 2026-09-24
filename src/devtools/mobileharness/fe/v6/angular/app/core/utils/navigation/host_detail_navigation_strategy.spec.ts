import {HostDetailNavigationStrategy} from './host_detail_navigation_strategy';

describe('HostDetailNavigationStrategy', () => {
  let strategy: HostDetailNavigationStrategy;

  beforeEach(() => {
    strategy = new HostDetailNavigationStrategy();
  });

  it('should have correct pageType', () => {
    expect(strategy.pageType).toBe('host');
  });

  it('should build correct route path', () => {
    expect(strategy.buildRoutePath({hostName: 'host_123'})).toBe('/hosts/host_123');
  });

  it('should extract non-1p universe in toCsnQueryParams and omit host_ip', () => {
    const params = strategy.toCsnQueryParams({
      'hostIp': '10.0.0.1',
      'universe': 'ats_ctrl_1',
    });
    expect(params).toEqual({
      'universe': 'ats_ctrl_1',
    });
  });

  it('should omit google_1p universe and host_ip in toCsnQueryParams', () => {
    const params = strategy.toCsnQueryParams({
      'hostIp': '10.0.0.1',
      'universe': 'google_1p',
    });
    expect(params).toEqual({});
  });

  it('should build correct external payload', () => {
    const payload = strategy.toExternalPayload(
      {
        hostName: 'host_abc',
        hostIp: '10.0.0.1',
        universe: 'ats_ctrl_1',
      },
      {'debug': 'true'},
      {'custom': 'val'},
    );
    expect(payload).toEqual({
      page: 'host_details',
      params: {
        'host_name': 'host_abc',
        'host_ip': '10.0.0.1',
        debug: 'true',
        custom: 'val',
        universe: 'ats_ctrl_1',
      },
    });
  });
});
