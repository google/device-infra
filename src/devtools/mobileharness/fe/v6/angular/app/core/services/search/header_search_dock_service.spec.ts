import {TestBed} from '@angular/core/testing';

import {HeaderSearchDockService} from './header_search_dock_service';

describe('HeaderSearchDockService', () => {
  let service: HeaderSearchDockService;
  let dockContainer: HTMLElement;
  let bodyContainer: HTMLElement;
  let searchInputEl: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [HeaderSearchDockService],
    });
    service = TestBed.inject(HeaderSearchDockService);

    dockContainer = document.createElement('div');
    bodyContainer = document.createElement('div');
    searchInputEl = document.createElement('div');
    bodyContainer.appendChild(searchInputEl);
  });

  it('should register and unregister dock container', () => {
    expect(service.dockContainer()).toBeNull();
    service.registerDockContainer(dockContainer);
    expect(service.dockContainer()).toBe(dockContainer);

    service.unregisterDockContainer();
    expect(service.dockContainer()).toBeNull();
    expect(service.isDocked()).toBeFalse();
  });

  it('should dock search input element into dock container', () => {
    service.registerDockContainer(dockContainer);
    service.dock(searchInputEl);

    expect(searchInputEl.parentElement).toBe(dockContainer);
    expect(service.isDocked()).toBeTrue();
  });

  it('should undock search input element into target parent container', () => {
    service.registerDockContainer(dockContainer);
    service.dock(searchInputEl);
    expect(searchInputEl.parentElement).toBe(dockContainer);

    service.undock(searchInputEl, bodyContainer);
    expect(searchInputEl.parentElement).toBe(bodyContainer);
    expect(service.isDocked()).toBeFalse();
  });

  it('should cleanup element on destroy', () => {
    service.registerDockContainer(dockContainer);
    service.dock(searchInputEl);
    expect(searchInputEl.parentElement).toBe(dockContainer);

    service.cleanup(searchInputEl);
    expect(searchInputEl.parentElement).toBeNull();
    expect(service.isDocked()).toBeFalse();
  });
});
