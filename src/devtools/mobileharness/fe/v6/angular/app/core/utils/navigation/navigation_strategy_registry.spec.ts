import {HomeNavigationStrategy} from './home_navigation_strategy';
import {NavigationStrategyRegistry} from './navigation_strategy_registry';
import {SearchNavigationStrategy} from './search_navigation_strategy';

describe('NavigationStrategyRegistry', () => {
  it('should retrieve strategies for all standard page types', () => {
    expect(NavigationStrategyRegistry.getRequired('device')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('host')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('job')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('test')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('session')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('device_search')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('host_search')).toBeDefined();
    expect(NavigationStrategyRegistry.getRequired('home')).toBeDefined();
  });

  it('should use SearchNavigationStrategy for search page types', () => {
    const deviceSearch = NavigationStrategyRegistry.getRequired('device_search');
    expect(deviceSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(deviceSearch.buildRoutePath({})).toBe('/devices');

    const hostSearch = NavigationStrategyRegistry.getRequired('host_search');
    expect(hostSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(hostSearch.buildRoutePath({})).toBe('/hosts');

    const sessionSearch = NavigationStrategyRegistry.getRequired('session_search');
    expect(sessionSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(sessionSearch.buildRoutePath({})).toBe('/sessions');

    const jobSearch = NavigationStrategyRegistry.getRequired('job_search');
    expect(jobSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(jobSearch.buildRoutePath({})).toBe('/jobs');

    const testSearch = NavigationStrategyRegistry.getRequired('test_search');
    expect(testSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(testSearch.buildRoutePath({})).toBe('/tests');
  });

  it('should correctly configure supportsExternalNavigation and external payload', () => {
    const deviceStrategy = NavigationStrategyRegistry.getRequired('device');
    expect(deviceStrategy.supportsExternalNavigation).toBeTrue();

    const homeStrategy = NavigationStrategyRegistry.getRequired('home');
    expect(homeStrategy.supportsExternalNavigation).toBeFalse();
    expect(() => homeStrategy.toExternalPayload({})).toThrowError(
      /Does NOT support to notify parent for page type: home/,
    );

    const searchStrategy = NavigationStrategyRegistry.getRequired('device_search');
    expect(searchStrategy.supportsExternalNavigation).toBeFalse();
    expect(() => searchStrategy.toExternalPayload({})).toThrowError(
      /Does NOT support to notify parent for page type: device_search/,
    );
  });

  it('should filter parameters according to allowedParams in SearchNavigationStrategy', () => {
    const deviceSearch = NavigationStrategyRegistry.getRequired('device_search');
    const queryParams = deviceSearch.toCsnQueryParams({
      'f': 'status:READY',
      'gb': 'host',
      'fleet': 'ats',
      'q': 'device-id-for-ambiguous-1',
      'unknown_param': 'ignore_me',
    });
    expect(queryParams).toEqual({
      'f': 'status:READY',
      'gb': 'host',
      'fleet': 'ats',
      'q': 'device-id-for-ambiguous-1',
    });
  });

  it('should configure all_site_search strategy with /search and allowedParams q and scope', () => {
    const allSiteSearch =
      NavigationStrategyRegistry.getRequired('all_site_search');
    expect(allSiteSearch instanceof SearchNavigationStrategy).toBeTrue();
    expect(allSiteSearch.buildRoutePath({})).toBe('/search');
    expect(allSiteSearch.supportsExternalNavigation).toBeFalse();

    const queryParams = allSiteSearch.toCsnQueryParams({
      'q': 'device-id-for-1-hit-1',
      'scope': 'device',
      'unknown_param': 'ignore_me',
    });
    expect(queryParams).toEqual({
      'q': 'device-id-for-1-hit-1',
      'scope': 'device',
    });
  });

  it('should use HomeNavigationStrategy for home page type', () => {
    const homeStrategy = NavigationStrategyRegistry.getRequired('home');
    expect(homeStrategy instanceof HomeNavigationStrategy).toBeTrue();
    expect(homeStrategy.buildRoutePath({})).toBe('/home');
  });

  it('should throw error for unknown page type in getRequired', () => {
    expect(() =>
      NavigationStrategyRegistry.getRequired('unknown_page_type'),
    ).toThrowError(/No navigation strategy registered/);
  });
});
