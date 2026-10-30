import {
  ConnectedPosition,
  FlexibleConnectedPositionStrategy,
  Overlay,
  OverlayRef,
} from '@angular/cdk/overlay';
import {ComponentPortal} from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ComponentRef,
  DestroyRef,
  Directive,
  effect,
  ElementRef,
  inject,
  input,
} from '@angular/core';

/**
 * Material 3 light-surface tooltip card rendered inside a CDK overlay for
 * multi-sentence search key descriptions.
 */
@Component({
  selector: 'app-key-description-tooltip-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'role': 'tooltip',
  },
  template: `
    <div class="key-description-tooltip-card">{{ description() }}</div>
  `,
  styles: [
    `
      :host {
        display: block;
        pointer-events: none;
      }

      .key-description-tooltip-card {
        box-sizing: border-box;
        max-width: min(320px, calc(100vw - 16px));
        padding: 10px 14px;
        background-color: #ffffff;
        color: #3c4043;
        font-family: Roboto, 'Helvetica Neue', sans-serif;
        font-size: 12.5px;
        font-weight: 400;
        line-height: 18px;
        letter-spacing: 0.1px;
        text-align: left;
        text-transform: none;
        white-space: normal;
        word-break: break-word;
        border-radius: 10px;
        border: 1px solid #dadce0;
        box-shadow:
          0 4px 12px rgba(60, 64, 67, 0.16),
          0 1px 3px rgba(60, 64, 67, 0.08);
        pointer-events: none;
      }
    `,
  ],
})
export class KeyDescriptionTooltipCard {
  readonly description = input<string>('');
}

/**
 * Displays a Material 3 light-surface tooltip card left-aligned with the
 * target key label (`[tooltipAlignOrigin]`) when space permits on the right,
 * falling back to right-edge alignment near the viewport border.
 */
@Directive({
  selector: '[appKeyDescriptionTooltip]',
  standalone: true,
  host: {
    '(mouseenter)': 'show()',
    '(mouseleave)': 'hide()',
    '(focusin)': 'show()',
    '(focusout)': 'hide()',
    '(keydown.escape)': 'onEscape($event)',
  },
})
export class KeyDescriptionTooltipDirective {
  private readonly overlay = inject(Overlay);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly description = input<string>('', {alias: 'appKeyDescriptionTooltip'});
  readonly alignOrigin = input<HTMLElement | ElementRef<HTMLElement> | null>(
    null,
    {alias: 'tooltipAlignOrigin'},
  );
  readonly preferredSide = input<'above' | 'below'>('above', {
    alias: 'tooltipPreferredSide',
  });

  private overlayRef: OverlayRef | null = null;
  private positionStrategy: FlexibleConnectedPositionStrategy | null = null;
  private cardRef: ComponentRef<KeyDescriptionTooltipCard> | null = null;

  private readonly onCaptureScroll = (event: Event): void => {
    const target = event.target;
    if (
      target instanceof Node &&
      target.contains(this.elementRef.nativeElement)
    ) {
      this.hide();
    }
  };

  constructor() {
    effect(() => {
      const text = this.description().trim();
      if (!text) {
        this.hide();
        return;
      }
      if (this.cardRef && this.overlayRef?.hasAttached()) {
        this.cardRef.setInput('description', text);
        this.cardRef.changeDetectorRef.detectChanges();
        this.overlayRef.updatePosition();
      }
    });

    this.destroyRef.onDestroy(() => {
      this.overlayRef?.dispose();
    });
  }

  onEscape(event: Event): void {
    if (this.overlayRef?.hasAttached()) {
      event.stopPropagation();
      this.hide();
    }
  }

  show(): void {
    const text = this.description().trim();
    if (!text) {
      return;
    }

    const origin = this.resolveOriginElement();
    if (!this.overlayRef || !this.positionStrategy) {
      this.positionStrategy = this.overlay
        .position()
        .flexibleConnectedTo(origin)
        .withPositions(this.buildPositions())
        .withViewportMargin(8)
        .withPush(true)
        .withLockedPosition(false);

      this.overlayRef = this.overlay.create({
        positionStrategy: this.positionStrategy,
        panelClass: 'key-description-tooltip-pane',
      });
      this.overlayRef.detachments().subscribe(() => {
        this.elementRef.nativeElement.ownerDocument.removeEventListener(
          'scroll',
          this.onCaptureScroll,
          true,
        );
      });
    } else {
      this.positionStrategy
        .setOrigin(origin)
        .withPositions(this.buildPositions());
    }

    if (!this.overlayRef.hasAttached()) {
      this.elementRef.nativeElement.ownerDocument.addEventListener(
        'scroll',
        this.onCaptureScroll,
        true,
      );
      this.cardRef = this.overlayRef.attach(
        new ComponentPortal(KeyDescriptionTooltipCard),
      );
    }
    this.cardRef?.setInput('description', text);
    this.cardRef?.changeDetectorRef.detectChanges();
    this.overlayRef.updatePosition();
  }

  hide(): void {
    this.overlayRef?.detach();
  }

  private resolveOriginElement(): HTMLElement {
    const candidate = this.alignOrigin();
    if (!candidate) {
      return this.elementRef.nativeElement;
    }
    if (candidate instanceof ElementRef) {
      return candidate.nativeElement;
    }
    return candidate;
  }

  private buildPositions(): ConnectedPosition[] {
    const aboveStart: ConnectedPosition = {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -6,
    };
    const belowStart: ConnectedPosition = {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 6,
    };
    const aboveEnd: ConnectedPosition = {
      originX: 'end',
      originY: 'top',
      overlayX: 'end',
      overlayY: 'bottom',
      offsetY: -6,
    };
    const belowEnd: ConnectedPosition = {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top',
      offsetY: 6,
    };

    if (this.preferredSide() === 'below') {
      return [belowStart, aboveStart, belowEnd, aboveEnd];
    }
    return [aboveStart, belowStart, aboveEnd, belowEnd];
  }
}
