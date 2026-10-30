import {OverlayContainer} from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  signal,
  viewChild,
} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';

import {KeyDescriptionTooltipDirective} from './key_description_tooltip';

@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KeyDescriptionTooltipDirective],
  template: `
    <div id="unrelated-scroll" style="overflow: auto;"></div>
    <div
      id="scroll-container"
      style="position: fixed; top: 65px; overflow: auto;"
      [style.left.px]="alignNearRightEdge() ? null : 40"
      [style.right.px]="alignNearRightEdge() ? 20 : null"
    >
      <span #altOrigin id="alt-origin" style="display: inline-block; width: 20px;">
      </span>
      <span
        #labelWrap
        id="label-wrap"
        style="display: inline-flex; align-items: center; gap: 4px;"
      >
        <span>Version</span>
        <span
          id="info-trigger"
          tabindex="0"
          [appKeyDescriptionTooltip]="description()"
          [tooltipAlignOrigin]="resolvedAlignOrigin()"
          [tooltipPreferredSide]="preferredSide()"
        >
          info
        </span>
      </span>
    </div>
  `,
})
class TestHostComponent {
  readonly description = signal('Device OS version.');
  readonly preferredSide = signal<'above' | 'below'>('below');
  readonly alignNearRightEdge = signal(false);
  readonly originMode = signal<'element' | 'elementRef' | 'null'>('element');

  readonly labelWrapRef =
    viewChild.required<ElementRef<HTMLElement>>('labelWrap');
  readonly altOriginRef =
    viewChild.required<ElementRef<HTMLElement>>('altOrigin');

  resolvedAlignOrigin(): HTMLElement | ElementRef<HTMLElement> | null {
    const mode = this.originMode();
    if (mode === 'null') {
      return null;
    }
    if (mode === 'elementRef') {
      return this.altOriginRef();
    }
    return this.labelWrapRef().nativeElement;
  }
}

describe('KeyDescriptionTooltipDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let overlayContainerElement: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    overlayContainerElement =
      TestBed.inject(OverlayContainer).getContainerElement();
    fixture = TestBed.createComponent(TestHostComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    fixture.nativeElement.remove();
  });

  it('opens on mouseenter left-aligned with tooltipAlignOrigin and closes on mouseleave', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    const labelWrap = fixture.nativeElement.querySelector(
      '#label-wrap',
    ) as HTMLElement;

    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));

    const card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card).not.toBeNull();
    expect(card?.textContent?.trim()).toBe('Device OS version.');

    const labelLeft = Math.round(labelWrap.getBoundingClientRect().left);
    const cardLeft = Math.round(card!.getBoundingClientRect().left);
    expect(Math.abs(cardLeft - labelLeft)).toBeLessThanOrEqual(1);

    trigger.triggerEventHandler('mouseleave', new MouseEvent('mouseleave'));

    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();
  });

  it('falls back to right-edge alignment near the right viewport border', () => {
    fixture.componentInstance.alignNearRightEdge.set(true);
    fixture.detectChanges();

    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    const labelWrap = fixture.nativeElement.querySelector(
      '#label-wrap',
    ) as HTMLElement;

    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));
    fixture.detectChanges();

    const card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card).not.toBeNull();

    const labelRight = Math.round(labelWrap.getBoundingClientRect().right);
    const cardRight = Math.round(card!.getBoundingClientRect().right);
    expect(Math.abs(cardRight - labelRight)).toBeLessThanOrEqual(1);
  });

  it('opens on focusin, stops Escape propagation only while open, and closes on focusout', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));

    const initialClosedEscape = new KeyboardEvent('keydown', {key: 'Escape'});
    spyOn(initialClosedEscape, 'stopPropagation');
    trigger.triggerEventHandler('keydown.escape', initialClosedEscape);
    expect(initialClosedEscape.stopPropagation).not.toHaveBeenCalled();

    trigger.triggerEventHandler('focusin', new FocusEvent('focusin'));
    fixture.detectChanges();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).not.toBeNull();

    const openEscape = new KeyboardEvent('keydown', {key: 'Escape'});
    spyOn(openEscape, 'stopPropagation');
    trigger.triggerEventHandler('keydown.escape', openEscape);
    fixture.detectChanges();
    expect(openEscape.stopPropagation).toHaveBeenCalled();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();

    const detachedEscape = new KeyboardEvent('keydown', {key: 'Escape'});
    spyOn(detachedEscape, 'stopPropagation');
    trigger.triggerEventHandler('keydown.escape', detachedEscape);
    expect(detachedEscape.stopPropagation).not.toHaveBeenCalled();

    trigger.triggerEventHandler('focusin', new FocusEvent('focusin'));
    fixture.detectChanges();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).not.toBeNull();

    trigger.triggerEventHandler('focusout', new FocusEvent('focusout'));
    fixture.detectChanges();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();
  });

  it('closes when an ancestor container scrolls, ignores sibling scrolls, and cleans up on detach', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    const directive = trigger.injector.get(KeyDescriptionTooltipDirective);
    const scrollContainer = fixture.nativeElement.querySelector(
      '#scroll-container',
    ) as HTMLElement;
    const unrelatedScroll = fixture.nativeElement.querySelector(
      '#unrelated-scroll',
    ) as HTMLElement;

    trigger.triggerEventHandler('focusin', new FocusEvent('focusin'));
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).not.toBeNull();

    // Non-Node scroll targets and unrelated sibling containers do not close the tooltip.
    document.dispatchEvent(new Event('scroll'));
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();

    trigger.triggerEventHandler('focusin', new FocusEvent('focusin'));
    const fakeEvent = {target: window} as unknown as Event;
    spyOnProperty(Event.prototype, 'target', 'get').and.callFake(function (
      this: Event,
    ) {
      return fakeEvent.target;
    });
    document.dispatchEvent(new Event('scroll'));
    (
      Object.getOwnPropertyDescriptor(Event.prototype, 'target')
        ?.get as jasmine.Spy
    ).and.callThrough();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).not.toBeNull();

    unrelatedScroll.dispatchEvent(new Event('scroll'));
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).not.toBeNull();

    // Scrolling an ancestor container closes the tooltip even though DOM scroll does not bubble.
    scrollContainer.dispatchEvent(new Event('scroll'));
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();

    // Once detached, capture-phase scroll listener is removed so hide() is not invoked again.
    const hideSpy = spyOn(directive, 'hide').and.callThrough();
    scrollContainer.dispatchEvent(new Event('scroll'));
    expect(hideSpy).not.toHaveBeenCalled();
  });

  it('does not open when description is blank', () => {
    fixture.componentInstance.description.set('   ');
    fixture.detectChanges();

    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();
  });

  it('updates live description text and detaches when cleared while open', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));
    fixture.detectChanges();

    const card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card?.textContent?.trim()).toBe('Device OS version.');

    fixture.componentInstance.description.set('Updated version description.');
    fixture.detectChanges();
    expect(card?.textContent?.trim()).toBe('Updated version description.');

    fixture.componentInstance.description.set('   ');
    fixture.detectChanges();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-card'),
    ).toBeNull();
  });

  it('recomputes origin and side on subsequent opens for null and ElementRef origins', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    const triggerEl = trigger.nativeElement as HTMLElement;
    const altOriginEl = fixture.nativeElement.querySelector(
      '#alt-origin',
    ) as HTMLElement;

    // First open: ElementRef origin below.
    fixture.componentInstance.originMode.set('elementRef');
    fixture.componentInstance.preferredSide.set('below');
    fixture.detectChanges();

    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));
    fixture.detectChanges();

    let card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card).not.toBeNull();
    expect(
      Math.abs(
        Math.round(card!.getBoundingClientRect().left) -
          Math.round(altOriginEl.getBoundingClientRect().left),
      ),
    ).toBeLessThanOrEqual(1);
    expect(card!.getBoundingClientRect().top).toBeGreaterThanOrEqual(
      altOriginEl.getBoundingClientRect().bottom,
    );

    // While still attached, update origin + side and trigger focusin to verify
    // show() repositions an already-attached overlay via updatePosition().
    fixture.componentInstance.originMode.set('null');
    fixture.componentInstance.preferredSide.set('above');
    fixture.detectChanges();

    trigger.triggerEventHandler('focusin', new FocusEvent('focusin'));

    card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card).not.toBeNull();
    expect(
      Math.abs(
        Math.round(card!.getBoundingClientRect().left) -
          Math.round(triggerEl.getBoundingClientRect().left),
      ),
    ).toBeLessThanOrEqual(1);
    expect(card!.getBoundingClientRect().bottom).toBeLessThanOrEqual(
      triggerEl.getBoundingClientRect().top,
    );

    trigger.triggerEventHandler('mouseleave', new MouseEvent('mouseleave'));

    // Reopen after detach to verify existing overlayRef re-attaches cleanly.
    fixture.componentInstance.originMode.set('element');
    fixture.componentInstance.preferredSide.set('below');
    fixture.detectChanges();

    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));

    card = overlayContainerElement.querySelector(
      '.key-description-tooltip-card',
    ) as HTMLElement | null;
    expect(card).not.toBeNull();
  });

  it('disposes the overlay when the host component is destroyed while open', () => {
    const trigger = fixture.debugElement.query(By.css('#info-trigger'));
    trigger.triggerEventHandler('mouseenter', new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-pane'),
    ).not.toBeNull();

    fixture.destroy();
    expect(
      overlayContainerElement.querySelector('.key-description-tooltip-pane'),
    ).toBeNull();
  });
});
