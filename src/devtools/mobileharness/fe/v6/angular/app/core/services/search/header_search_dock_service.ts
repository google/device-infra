import {inject, Injectable, RendererFactory2, signal} from '@angular/core';

/**
 * Service managing the dynamic docking of the search input into the global app toolbar.
 * Decouples AppComponent (which hosts the toolbar dock) from routed search components.
 */
@Injectable({providedIn: 'root'})
export class HeaderSearchDockService {
  private readonly renderer = inject(RendererFactory2).createRenderer(
    null,
    null,
  );

  /** Reference to the header search dock container element in the global toolbar. */
  readonly dockContainer = signal<HTMLElement | null>(null);

  /** Whether the search input is currently docked in the global toolbar. */
  readonly isDocked = signal<boolean>(false);

  /** Registers the global toolbar dock container element. */
  registerDockContainer(element: HTMLElement) {
    this.dockContainer.set(element);
  }

  /** Unregisters the global toolbar dock container element on component destroy. */
  unregisterDockContainer() {
    this.dockContainer.set(null);
    this.isDocked.set(false);
  }

  /** Docks the given search input element into the global toolbar dock container. */
  dock(element: HTMLElement) {
    const container = this.dockContainer();
    if (!container) return;

    const activeEl = element.ownerDocument?.activeElement as HTMLElement | null;
    const hadFocus = Boolean(activeEl && element.contains(activeEl));

    if (element.parentElement !== container) {
      this.renderer.appendChild(container, element);
    }
    this.isDocked.set(true);

    if (hadFocus && activeEl) {
      activeEl.focus();
    }
  }

  /** Undocks the search input element, returning it to its original body parent container. */
  undock(element: HTMLElement, targetParent: HTMLElement) {
    const activeEl = element.ownerDocument?.activeElement as HTMLElement | null;
    const hadFocus = Boolean(activeEl && element.contains(activeEl));

    if (element.parentElement !== targetParent) {
      this.renderer.appendChild(targetParent, element);
    }
    this.isDocked.set(false);

    if (hadFocus && activeEl) {
      activeEl.focus();
    }
  }

  /** Cleans up the search input element from the dock container on destroy. */
  cleanup(element: HTMLElement) {
    const container = this.dockContainer();
    if (container && element.parentElement === container) {
      this.renderer.removeChild(container, element);
    }
    this.isDocked.set(false);
  }
}
