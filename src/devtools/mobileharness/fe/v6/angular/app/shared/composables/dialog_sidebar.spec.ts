import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';

import {
  findActiveSection,
  ResizableSidebar,
  ResizableSidebarOptions,
  useResizableSidebar,
} from './dialog_sidebar';

const STORAGE_KEY = 'dialog_sidebar_test_icon_only';

function createSidebar(
  options: Partial<ResizableSidebarOptions> = {},
): ResizableSidebar {
  return TestBed.runInInjectionContext(() =>
    useResizableSidebar({
      storageKey: STORAGE_KEY,
      layoutWidth: () => 1000,
      ...options,
    }),
  );
}

function drag(sidebar: ResizableSidebar, fromX: number, toXs: number[]) {
  sidebar.startResize(new MouseEvent('mousedown', {clientX: fromX}));
  for (const clientX of toXs) {
    window.dispatchEvent(new MouseEvent('mousemove', {clientX}));
  }
}

describe('useResizableSidebar', () => {
  beforeEach(() => {
    window.localStorage.removeItem(STORAGE_KEY);
  });

  afterEach(() => {
    window.dispatchEvent(new MouseEvent('mouseup'));
    window.localStorage.removeItem(STORAGE_KEY);
  });

  it('caps the width at half of the layout while dragging', () => {
    const sidebar = createSidebar();
    expect(sidebar.width()).toBe(300);

    drag(sidebar, 300, [900]);

    expect(sidebar.isResizing()).toBeTrue();
    expect(sidebar.width()).toBe(500);
  });

  it('snaps into the icon rail below the minimum width and remembers it', () => {
    const sidebar = createSidebar();

    drag(sidebar, 300, [190, 184]);

    expect(sidebar.width()).toBe(56);
    expect(sidebar.isCompact()).toBeTrue();
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('true');

    window.dispatchEvent(new MouseEvent('mouseup'));
    expect(sidebar.isResizing()).toBeFalse();
  });

  it('opens the icon rail only once dragged past the threshold', () => {
    window.localStorage.setItem(STORAGE_KEY, 'true');
    const sidebar = createSidebar();
    sidebar.restorePreference();
    expect(sidebar.width()).toBe(56);

    drag(sidebar, 56, [101]);
    expect(sidebar.width()).toBe(56);

    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 102}));
    expect(sidebar.width()).toBe(195);
    expect(sidebar.isCompact()).toBeFalse();
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('false');
  });

  it('expands the icon rail to the default width and remembers it', () => {
    window.localStorage.setItem(STORAGE_KEY, 'true');
    const sidebar = createSidebar();
    sidebar.restorePreference();

    sidebar.expand();

    expect(sidebar.width()).toBe(300);
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('false');
  });

  it('restores the preference only once', () => {
    window.localStorage.setItem(STORAGE_KEY, 'true');
    const sidebar = createSidebar();
    sidebar.restorePreference();
    sidebar.expand();
    window.localStorage.setItem(STORAGE_KEY, 'true');

    sidebar.restorePreference();

    expect(sidebar.width()).toBe(300);
  });

  it('neither restores nor saves the preference while it is disabled', () => {
    window.localStorage.setItem(STORAGE_KEY, 'true');
    const preferenceEnabled = signal(false);
    const sidebar = createSidebar({preferenceEnabled});

    sidebar.restorePreference();
    sidebar.expand();

    expect(sidebar.width()).toBe(300);
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('true');

    preferenceEnabled.set(true);
    sidebar.restorePreference();

    expect(sidebar.width()).toBe(56);
  });
});

describe('findActiveSection', () => {
  function createContainer(scrollTop: number, scrollHeight = 1200) {
    const container = document.createElement('div');
    spyOnProperty(container, 'scrollHeight').and.returnValue(scrollHeight);
    spyOnProperty(container, 'clientHeight').and.returnValue(400);
    spyOnProperty(container, 'scrollTop').and.returnValue(scrollTop);
    spyOn(container, 'getBoundingClientRect').and.returnValue({
      top: 0,
      height: 400,
    } as DOMRect);
    return container;
  }

  function createSections(...tops: number[]) {
    return tops.map((top) => {
      const section = document.createElement('section');
      spyOn(section, 'getBoundingClientRect').and.returnValue({top} as DOMRect);
      return section;
    });
  }

  it('returns undefined without sections or room to scroll', () => {
    expect(findActiveSection(createContainer(0), [])).toBeUndefined();
    expect(
      findActiveSection(createContainer(0, 402), createSections(0, 100)),
    ).toBeUndefined();
  });

  it('activates the first section at the top', () => {
    expect(
      findActiveSection(createContainer(4), createSections(-4, 300, 600)),
    ).toEqual({index: 0, isAtTop: true});
  });

  it('activates the last section at the bottom', () => {
    expect(
      findActiveSection(createContainer(796), createSections(-796, -200, 350)),
    ).toEqual({index: 2, isAtTop: false});
  });

  it('activates the last section above the trigger line in between', () => {
    // Halfway down, the trigger line is at 48 + 0.5 * (400 - 2 * 48) = 200.
    expect(
      findActiveSection(createContainer(400), createSections(-400, 200, 201)),
    ).toEqual({index: 1, isAtTop: false});
  });
});
