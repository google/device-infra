import {PortalModule} from '@angular/cdk/portal';
import {Component, inject} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';

import {HeaderSearchDockService} from './header_search_dock_service';

@Component({
  standalone: true,
  imports: [PortalModule],
  template: `
    <div class="dock-container">
      <ng-template [cdkPortalOutlet]="service.activePortal()"></ng-template>
    </div>
  `,
})
class TestHostComponent {
  readonly service = inject(HeaderSearchDockService);
}

describe('HeaderSearchDockService', () => {
  let service: HeaderSearchDockService;
  let fixture: ComponentFixture<TestHostComponent>;
  let dockContainer: HTMLElement;
  let bodyContainer: HTMLElement;
  let searchInputEl: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [HeaderSearchDockService],
    });
    fixture = TestBed.createComponent(TestHostComponent);
    service = fixture.componentInstance.service;
    fixture.detectChanges();

    dockContainer = fixture.nativeElement.querySelector('.dock-container')!;
    bodyContainer = document.createElement('div');
    document.body.appendChild(bodyContainer);
    searchInputEl = document.createElement('div');
    bodyContainer.appendChild(searchInputEl);
  });

  afterEach(() => {
    bodyContainer.remove();
  });

  it('should dock search input element into dock container via CDK portal', () => {
    service.dock(searchInputEl);
    fixture.detectChanges();

    expect(searchInputEl.parentElement).toBe(dockContainer);
    expect(service.isDocked()).toBeTrue();
    expect(service.activePortal()).not.toBeNull();
  });

  it('should undock search input element into target parent container via CDK portal', () => {
    service.dock(searchInputEl);
    fixture.detectChanges();
    expect(searchInputEl.parentElement).toBe(dockContainer);

    service.undock(searchInputEl);
    fixture.detectChanges();
    expect(searchInputEl.parentElement).toBe(bodyContainer);
    expect(service.isDocked()).toBeFalse();
    expect(service.activePortal()).toBeNull();
  });

  it('should cleanup element on destroy and restore to body container', () => {
    service.dock(searchInputEl);
    fixture.detectChanges();
    expect(searchInputEl.parentElement).toBe(dockContainer);

    service.cleanup(searchInputEl);
    fixture.detectChanges();
    expect(searchInputEl.parentElement).toBe(bodyContainer);
    expect(service.isDocked()).toBeFalse();
    expect(service.activePortal()).toBeNull();
  });

  it('should create and clear DomPortal when docking and undocking', () => {
    expect(service.activePortal()).toBeNull();
    service.dock(searchInputEl);
    expect(service.activePortal()).not.toBeNull();
    expect(service.isDocked()).toBeTrue();

    service.undock();
    expect(service.activePortal()).toBeNull();
    expect(service.isDocked()).toBeFalse();
  });

  it('should ignore redundant dock call when the same element is already docked', () => {
    service.dock(searchInputEl);
    const firstPortal = service.activePortal();
    expect(firstPortal).not.toBeNull();

    service.dock(searchInputEl);
    expect(service.activePortal()).toBe(firstPortal);
  });

  it('should restore focus via requestAnimationFrame when docking and undocking a focused child element', () => {
    const childInput = document.createElement('input');
    searchInputEl.appendChild(childInput);
    childInput.focus();
    expect(document.activeElement).toBe(childInput);

    const focusSpy = spyOn(childInput, 'focus').and.callThrough();
    spyOn(window, 'requestAnimationFrame').and.callFake(
      (cb: FrameRequestCallback) => {
        cb(0);
        return 0;
      },
    );

    service.dock(searchInputEl);
    expect(focusSpy).toHaveBeenCalledTimes(1);

    childInput.focus();
    focusSpy.calls.reset();

    service.undock(searchInputEl);
    expect(focusSpy).toHaveBeenCalledTimes(1);
  });

  it('should not undock or cleanup when called with a different element than the currently docked one', () => {
    const otherEl = document.createElement('div');
    service.dock(searchInputEl);
    expect(service.isDocked()).toBeTrue();

    service.undock(otherEl);
    expect(service.isDocked()).toBeTrue();

    service.cleanup(otherEl);
    expect(service.isDocked()).toBeTrue();

    service.cleanup();
    expect(service.isDocked()).toBeFalse();
  });
});
