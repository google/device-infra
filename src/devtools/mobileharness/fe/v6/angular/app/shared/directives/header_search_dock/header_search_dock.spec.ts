import {PortalModule} from '@angular/cdk/portal';
import {Component, inject, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';

import {HeaderSearchDockService} from '../../services/header_search_dock_service';
import {HeaderSearchDockDirective} from './header_search_dock';

@Component({
  standalone: true,
  imports: [PortalModule, HeaderSearchDockDirective],
  template: `
    <div class="dock-container">
      <ng-template [cdkPortalOutlet]="service.activePortal()"></ng-template>
    </div>
    <div class="body-container">
      @if (renderInput()) {
        <div class="search-el" [headerSearchDock]="isDocked()"></div>
      }
    </div>
  `,
})
class DirectiveTestHostComponent {
  readonly service = inject(HeaderSearchDockService);
  readonly isDocked = signal(false);
  readonly renderInput = signal(true);
}

describe('HeaderSearchDockDirective', () => {
  let fixture: ComponentFixture<DirectiveTestHostComponent>;
  let component: DirectiveTestHostComponent;
  let service: HeaderSearchDockService;
  let dockContainer: HTMLElement;
  let bodyContainer: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DirectiveTestHostComponent],
      providers: [HeaderSearchDockService],
    });
    fixture = TestBed.createComponent(DirectiveTestHostComponent);
    component = fixture.componentInstance;
    service = component.service;
    fixture.detectChanges();

    dockContainer = fixture.nativeElement.querySelector('.dock-container')!;
    bodyContainer = fixture.nativeElement.querySelector('.body-container')!;
  });

  it('should dock element into dockContainer when headerSearchDock becomes true', () => {
    const searchEl = fixture.nativeElement.querySelector('.search-el')!;
    expect(searchEl.parentElement).toBe(bodyContainer);

    component.isDocked.set(true);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(searchEl.parentElement).toBe(dockContainer);
    expect(service.isDocked()).toBeTrue();
  });

  it('should undock element back to bodyContainer when headerSearchDock becomes false', () => {
    const searchEl = fixture.nativeElement.querySelector('.search-el')!;
    component.isDocked.set(true);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(searchEl.parentElement).toBe(dockContainer);

    component.isDocked.set(false);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(searchEl.parentElement).toBe(bodyContainer);
    expect(service.isDocked()).toBeFalse();
  });

  it('should cleanup element on directive destruction', () => {
    const searchEl = fixture.nativeElement.querySelector('.search-el')!;
    component.isDocked.set(true);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(searchEl.parentElement).toBe(dockContainer);

    component.renderInput.set(false);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(service.isDocked()).toBeFalse();
    expect(service.activePortal()).toBeNull();
  });
});
