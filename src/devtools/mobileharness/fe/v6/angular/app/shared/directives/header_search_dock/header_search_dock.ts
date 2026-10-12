import {DestroyRef, Directive, ElementRef, Input, inject} from '@angular/core';

import {HeaderSearchDockService} from '../../services/header_search_dock_service';

/**
 * Declarative directive to dock a host DOM element into the global header toolbar
 * when active, and automatically restore/cleanup on deactivation or component destruction.
 */
@Directive({
  selector: '[headerSearchDock]',
  standalone: true,
})
export class HeaderSearchDockDirective {
  private readonly dockService = inject(HeaderSearchDockService);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input()
  set headerSearchDock(docked: boolean) {
    const el = this.elementRef.nativeElement;
    if (docked) {
      this.dockService.dock(el);
    } else {
      this.dockService.undock(el);
    }
  }

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.dockService.cleanup(this.elementRef.nativeElement);
    });
  }
}
