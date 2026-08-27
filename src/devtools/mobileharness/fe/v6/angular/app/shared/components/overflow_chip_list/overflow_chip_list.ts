import {
  CdkOverlayOrigin,
  ConnectedPosition,
  OverlayModule,
} from '@angular/cdk/overlay';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import {
  SearchableListOverlayComponent,
  SearchableListOverlayData,
} from '../searchable_list_overlay/searchable_list_overlay';

/** Item descriptor for OverflowChipListComponent. */
export interface OverflowChipItem {
  label?: string;
  type?: string;
  value?: string;
  name?: string;
  cssClass?: string;
  isAbnormal?: boolean;
}

/**
 * Reusable component for displaying chips with an interactive "+N more" overflow button
 * that triggers a searchable overlay list.
 */
@Component({
  selector: 'app-overflow-chip-list',
  standalone: true,
  imports: [CommonModule, OverlayModule, SearchableListOverlayComponent],
  templateUrl: './overflow_chip_list.ng.html',
  styleUrl: './overflow_chip_list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverflowChipListComponent {
  readonly items = input.required<Array<string | OverflowChipItem>>();
  readonly limit = input<number>(1);
  readonly title = input<string>('Items');
  readonly subtitle = input<string>('');
  readonly defaultChipClass = input<string>('chip-normal');

  readonly activeOverlay = signal<{
    origin: CdkOverlayOrigin;
    data: SearchableListOverlayData;
  } | null>(null);

  readonly overlayPositions: ConnectedPosition[] = [
    // Priority 1: Right-aligned below
    {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top',
      offsetY: 8,
    },
    // Priority 1: Right-aligned above
    {
      originX: 'end',
      originY: 'top',
      overlayX: 'end',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Priority 2: Left-aligned below
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 8,
    },
    // Priority 2: Left-aligned above
    {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Fallback: Centered below
    {
      originX: 'center',
      originY: 'bottom',
      overlayX: 'center',
      overlayY: 'top',
      offsetY: 8,
    },
    // Fallback: Centered above
    {
      originX: 'center',
      originY: 'top',
      overlayX: 'center',
      overlayY: 'bottom',
      offsetY: -8,
    },
    // Fallback: Open to the left of the origin (button on the right of overlay)
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top',
      offsetY: 8,
    },
    // Fallback: Open to the right of the origin (button on the left of overlay)
    {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 8,
    },
  ];

  readonly normalizedItems = computed(() => {
    const raw = this.items() || [];
    return raw.map((item) => {
      if (typeof item === 'string') {
        return {
          label: item,
          cssClass: this.defaultChipClass(),
        };
      }
      if (item && typeof item === 'object') {
        const label =
          item.label || item.type || item.value || item.name || String(item);
        const cssClass =
          item.cssClass ||
          (item.isAbnormal ? 'chip-abnormal' : this.defaultChipClass());
        return {label, cssClass};
      }
      return {label: String(item), cssClass: this.defaultChipClass()};
    });
  });

  readonly displayedItems = computed(() => {
    return this.normalizedItems().slice(0, this.limit());
  });

  readonly remainingCount = computed(() => {
    return Math.max(0, this.normalizedItems().length - this.limit());
  });

  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  openOverlay(trigger: CdkOverlayOrigin) {
    this.clearHideTimer();
    this.activeOverlay.set({
      origin: trigger,
      data: {
        title: this.title() || 'Items',
        subtitle: this.subtitle(),
        type: 'chip',
        items: this.normalizedItems(),
      },
    });
  }

  onMoreHover(trigger: CdkOverlayOrigin) {
    this.openOverlay(trigger);
  }

  onMoreLeave(event?: MouseEvent) {
    this.scheduleHideOverlay();
  }

  onOverlayEnter() {
    this.clearHideTimer();
  }

  onOverlayLeave(event?: MouseEvent) {
    this.scheduleHideOverlay();
  }

  closeOverlay() {
    this.clearHideTimer();
    this.activeOverlay.set(null);
  }

  private clearHideTimer() {
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
  }

  private scheduleHideOverlay() {
    this.clearHideTimer();
    this.hideTimer = setTimeout(() => {
      this.activeOverlay.set(null);
    }, 200);
  }
}
