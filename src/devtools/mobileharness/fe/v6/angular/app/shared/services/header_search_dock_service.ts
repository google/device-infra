import {DomPortal} from '@angular/cdk/portal';
import {Injectable, computed, signal} from '@angular/core';

/**
 * Service managing the dynamic docking of the search input into the global app toolbar.
 * Decouples AppComponent (which hosts the toolbar portal outlet) from routed search components.
 */
@Injectable({providedIn: 'root'})
export class HeaderSearchDockService {
  /** Active portal projected into the global toolbar dock portal outlet. */
  readonly activePortal = signal<DomPortal | null>(null);

  /**
   * Whether the search input is currently docked in the global toolbar.
   * Derived reactively from activePortal as a single source of truth.
   */
  readonly isDocked = computed(() => this.activePortal() !== null);

  /** Docks the given search input element into the global toolbar via CDK DomPortal. */
  dock(element: HTMLElement) {
    const current = this.activePortal();
    if (current && current.element === element) {
      return;
    }

    const activeEl = element.ownerDocument?.activeElement as HTMLElement | null;
    const hadFocus = Boolean(activeEl && element.contains(activeEl));

    this.activePortal.set(new DomPortal(element));

    if (hadFocus && activeEl) {
      requestAnimationFrame(() => {
        activeEl.focus();
      });
    }
  }

  /** Undocks the search input element, restoring it to its original body parent container. */
  undock(element?: HTMLElement) {
    const current = this.activePortal();
    if (element && current && current.element !== element) {
      return;
    }

    this.activePortal.set(null);

    if (element) {
      const activeEl = element.ownerDocument
        ?.activeElement as HTMLElement | null;
      if (activeEl && element.contains(activeEl)) {
        requestAnimationFrame(() => {
          activeEl.focus();
        });
      }
    }
  }

  /** Cleans up the search input element from the dock container on destroy. */
  cleanup(element?: HTMLElement) {
    const current = this.activePortal();
    if (!element || (current && current.element === element)) {
      this.activePortal.set(null);
    }
  }
}
