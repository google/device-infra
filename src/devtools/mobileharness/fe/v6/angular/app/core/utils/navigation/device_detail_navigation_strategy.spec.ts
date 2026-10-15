import {DeviceDetailNavigationStrategy} from './device_detail_navigation_strategy';

describe('DeviceDetailNavigationStrategy', () => {
  let strategy: DeviceDetailNavigationStrategy;

  beforeEach(() => {
    strategy = new DeviceDetailNavigationStrategy();
  });

  it('should have correct pageType', () => {
    expect(strategy.pageType).toBe('device');
  });

  it('should build correct route path', () => {
    expect(strategy.buildRoutePath({deviceId: 'dev_123'})).toBe('/devices/dev_123');
  });

  it('should extract host_name and non-1p universe in toCsnQueryParams', () => {
    const params = strategy.toCsnQueryParams({
      'hostName': 'host_abc',
      'universe': 'ats_ctrl_1',
    });
    expect(params).toEqual({
      'host_name': 'host_abc',
      'universe': 'ats_ctrl_1',
    });
  });

  it('should omit google_1p universe in toCsnQueryParams', () => {
    const params = strategy.toCsnQueryParams({
      'hostName': 'host_abc',
      'universe': 'google_1p',
    });
    expect(params).toEqual({
      'host_name': 'host_abc',
    });
  });

  it('should build correct external payload', () => {
    const payload = strategy.toExternalPayload(
      {
        deviceId: 'dev_123',
        hostName: 'host_abc',
        hostIp: '10.0.0.1',
        universe: 'ats_ctrl_1',
      },
      {'debug': 'true'},
      {'custom': 'val'},
    );
    expect(payload).toEqual({
      page: 'device_details',
      params: {
        'host_name': 'host_abc',
        'host_ip': '10.0.0.1',
        debug: 'true',
        custom: 'val',
        universe: 'ats_ctrl_1',
        uuid: 'dev_123',
        'device_uuid': 'dev_123',
      },
    });
  });
});
