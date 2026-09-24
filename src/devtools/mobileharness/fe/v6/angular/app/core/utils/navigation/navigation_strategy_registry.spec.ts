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
    const searchStrategy = NavigationStrategyRegistry.getRequired('device_search');
    expect(searchStrategy instanceof SearchNavigationStrategy).toBeTrue();
    expect(searchStrategy.buildRoutePath({})).toBe('/devices');
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
