/**
 * @fileoverview Navigation sidebar behavior shared by the two-pane diagnostic
 * dialogs (e.g. HostDebugDialog and DeviceErrorDialog): a sidebar that can be
 * resized or collapsed into an icon-only rail, and a scroll-spy that keeps the
 * sidebar in sync with the section the user is reading in the main pane.
 */

import {computed, DestroyRef, inject, Signal, signal} from '@angular/core';

/**
 * How long scroll-spy ignores scroll events after the dialog scrolls the main
 * pane itself (e.g. to a section picked in the sidebar), in milliseconds.
 */
export const SCROLL_SPY_PAUSE_MS = 350;

/** Width of the collapsed icon-only rail, in pixels. */
const ICON_RAIL_WIDTH_PX = 56;

/** Initial width of the full sidebar, in pixels. */
const DEFAULT_SIDEBAR_WIDTH_PX = 300;

/** Sidebars narrower than this are shown as the icon-only rail, in pixels. */
const MIN_FULL_SIDEBAR_WIDTH_PX = 185;

/** Width the icon-only rail must be dragged past to open, in pixels. */
const RAIL_OPEN_THRESHOLD_PX = 101;

/** Narrowest width of a sidebar just dragged open from the rail, in pixels. */
const MIN_OPENED_FROM_RAIL_WIDTH_PX = MIN_FULL_SIDEBAR_WIDTH_PX + 10;

/** Scroll-spy ignores containers that can scroll less than this, in pixels. */
const MIN_SCROLL_RANGE_PX = 2;

/** Distance from the top or bottom that counts as being there, in pixels. */
const SCROLL_EDGE_TOLERANCE_PX = 4;

/** Inset of the scroll-spy trigger line from the container edges, in pixels. */
const TRIGGER_LINE_INSET_PX = 48;

/** Options for {@link useResizableSidebar}. */
export interface ResizableSidebarOptions {
  /** localStorage key remembering whether the sidebar is icon-only. */
  readonly storageKey: string;
  /** Returns the width of the area hosting the sidebar, or 0 if unknown. */
  readonly layoutWidth: () => number;
  /**
   * Returns whether the remembered icon-only preference applies right now.
   * While false, the preference is neither restored nor saved. Defaults to
   * always true.
   */
  readonly preferenceEnabled?: () => boolean;
}

/** Sidebar state and actions returned by {@link useResizableSidebar}. */
export interface ResizableSidebar {
  /** Current sidebar width, in pixels. */
  readonly width: Signal<number>;
  /** Whether the sidebar is collapsed into the icon-only rail. */
  readonly isCompact: Signal<boolean>;
  /** Whether the user is dragging the resize handle. */
  readonly isResizing: Signal<boolean>;
  /** Starts a drag; bind to the `mousedown` event of the resize handle. */
  readonly startResize: (event: MouseEvent) => void;
  /** Opens the icon-only rail back into the full sidebar. */
  readonly expand: () => void;
  /**
   * Collapses the sidebar into the rail if the user left it icon-only last
   * time. Only the first call made while the preference is enabled has an
   * effect, so that refreshing the dialog data keeps the current width.
   */
  readonly restorePreference: () => void;
}

/**
 * Composable for a sidebar that the user can resize by dragging a handle, and
 * that snaps into a compact icon-only rail when dragged too narrow. Whether
 * the sidebar is icon-only is remembered in localStorage.
 *
 * Must be called in an injection context, e.g. a component field initializer.
 */
export function useResizableSidebar(
  options: ResizableSidebarOptions,
): ResizableSidebar {
  const {storageKey, layoutWidth, preferenceEnabled = () => true} = options;
  const width = signal(DEFAULT_SIDEBAR_WIDTH_PX);
  const isCompact = computed(() => width() < MIN_FULL_SIDEBAR_WIDTH_PX);
  const isResizing = signal(false);
  let hasRestoredPreference = false;
  let removeDragListeners = () => {};

  const savePreference = () => {
    if (preferenceEnabled()) {
      writeStoredFlag(storageKey, isCompact());
    }
  };

  // The full sidebar may take up to half of the hosting area, if known.
  const getMaxWidth = (fallback: number) => {
    const availableWidth = layoutWidth();
    return availableWidth > 0
      ? Math.max(MIN_FULL_SIDEBAR_WIDTH_PX, Math.floor(availableWidth / 2))
      : fallback;
  };

  const stopResize = () => {
    removeDragListeners();
    removeDragListeners = () => {};
    isResizing.set(false);
    if (document.body.style.cursor === 'col-resize') {
      document.body.style.cursor = '';
    }
  };

  const startResize = (event: MouseEvent) => {
    event.preventDefault();
    stopResize();

    const startX = event.clientX;
    const startWidth = width();
    const maxWidth = getMaxWidth(Math.max(DEFAULT_SIDEBAR_WIDTH_PX, startWidth));
    const onMouseMove = (moveEvent: MouseEvent) => {
      const wasCompact = isCompact();
      const draggedWidth = startWidth + moveEvent.clientX - startX;
      width.set(getDraggedWidth(startWidth, draggedWidth, maxWidth));
      if (isCompact() !== wasCompact) {
        savePreference();
      }
    };

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', stopResize);
    removeDragListeners = () => {
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', stopResize);
    };
    isResizing.set(true);
    document.body.style.cursor = 'col-resize';
  };

  const expand = () => {
    const maxWidth = getMaxWidth(DEFAULT_SIDEBAR_WIDTH_PX);
    width.set(Math.min(maxWidth, DEFAULT_SIDEBAR_WIDTH_PX));
    savePreference();
  };

  const restorePreference = () => {
    if (hasRestoredPreference || !preferenceEnabled()) {
      return;
    }
    hasRestoredPreference = true;
    if (readStoredFlag(storageKey)) {
      width.set(ICON_RAIL_WIDTH_PX);
    }
  };

  inject(DestroyRef).onDestroy(stopResize);

  return {
    width: width.asReadonly(),
    isCompact,
    isResizing: isResizing.asReadonly(),
    startResize,
    expand,
    restorePreference,
  };
}

/**
 * Returns the sidebar width while its resize handle is dragged, snapping
 * between the full sidebar and the icon-only rail.
 *
 * @param startWidth Sidebar width when the drag started.
 * @param draggedWidth Width the sidebar would have if it followed the pointer.
 * @param maxWidth Widest the full sidebar may be.
 */
function getDraggedWidth(
  startWidth: number,
  draggedWidth: number,
  maxWidth: number,
): number {
  if (startWidth >= MIN_FULL_SIDEBAR_WIDTH_PX) {
    // Dragging the full sidebar: collapse into the rail once it is too narrow.
    return draggedWidth < MIN_FULL_SIDEBAR_WIDTH_PX
      ? ICON_RAIL_WIDTH_PX
      : Math.min(maxWidth, draggedWidth);
  }
  // Dragging the rail: open into the full sidebar once dragged far enough.
  return draggedWidth <= RAIL_OPEN_THRESHOLD_PX
    ? ICON_RAIL_WIDTH_PX
    : Math.min(maxWidth, Math.max(MIN_OPENED_FROM_RAIL_WIDTH_PX, draggedWidth));
}

/** Reads a boolean flag from localStorage, or false if it is unavailable. */
function readStoredFlag(key: string): boolean {
  try {
    return window.localStorage.getItem(key) === 'true';
  } catch {
    // localStorage can be missing or blocked, e.g. by privacy settings.
    return false;
  }
}

/** Writes a boolean flag to localStorage, if it is available. */
function writeStoredFlag(key: string, value: boolean) {
  try {
    window.localStorage.setItem(key, String(value));
  } catch {
    // Ignore quota or security errors; the flag is only a UI preference.
  }
}

/** Section that scroll-spy considers active, see {@link findActiveSection}. */
export interface ActiveSection {
  /** Index of the active section. */
  readonly index: number;
  /** Whether the container is scrolled to its very top. */
  readonly isAtTop: boolean;
}

/**
 * Scroll-spy: finds the section the user is reading in a scrollable container.
 *
 * At the top or bottom of the container, the first or last section is active.
 * In between, the active section is the last one whose top edge has passed a
 * trigger line that moves down the container as the user scrolls, so that
 * short trailing sections can also become active.
 *
 * @param container The scrollable container.
 * @param sections The section elements inside `container`, in DOM order.
 * @return The active section, or undefined if there are no sections or the
 *     container can barely scroll.
 */
export function findActiveSection(
  container: HTMLElement,
  sections: readonly HTMLElement[],
): ActiveSection | undefined {
  const maxScrollTop = container.scrollHeight - container.clientHeight;
  if (sections.length === 0 || maxScrollTop <= MIN_SCROLL_RANGE_PX) {
    return undefined;
  }
  const scrollTop = container.scrollTop;
  if (scrollTop <= SCROLL_EDGE_TOLERANCE_PX) {
    return {index: 0, isAtTop: true};
  }
  if (scrollTop >= maxScrollTop - SCROLL_EDGE_TOLERANCE_PX) {
    return {index: sections.length - 1, isAtTop: false};
  }

  const containerRect = container.getBoundingClientRect();
  const triggerLineTravel = Math.max(
    0,
    containerRect.height - 2 * TRIGGER_LINE_INSET_PX,
  );
  const triggerLineY =
    TRIGGER_LINE_INSET_PX + (scrollTop / maxScrollTop) * triggerLineTravel;
  let index = 0;
  sections.forEach((section, i) => {
    const sectionTop = section.getBoundingClientRect().top - containerRect.top;
    if (sectionTop <= triggerLineY) {
      index = i;
    }
  });
  return {index, isAtTop: false};
}

/**
 * Scrolls the navigation list of a sidebar so that its active item is visible,
 * or back to its top when the main pane is scrolled to the top. Runs in a
 * later task so that the item's active state has been rendered.
 *
 * @param sidebar The sidebar element containing a `<nav>` list.
 * @param activeItemSelector CSS selector matching the active navigation item.
 * @param isAtTop Whether the main pane is scrolled to its top.
 */
export function revealActiveNavItem(
  sidebar: HTMLElement | undefined,
  activeItemSelector: string,
  isAtTop: boolean,
) {
  setTimeout(() => {
    const nav = sidebar?.querySelector('nav');
    if (isAtTop && nav) {
      nav.scrollTop = 0;
      return;
    }
    sidebar
      ?.querySelector(activeItemSelector)
      ?.scrollIntoView({block: 'nearest'});
  });
}
