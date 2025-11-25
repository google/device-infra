import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  signal,
  SimpleChanges,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged, takeUntil} from 'rxjs/operators';
import type {DeviceOverview} from '../../../../core/models/device_overview';
import {
  HealthState,
  type DimensionSourceGroup,
} from '../../../../core/models/device_overview';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  NavItem,
  OverviewPage,
} from '../../../../shared/components/overview_page/overview_page';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';

interface HealthStateUI {
  icon: string;
  iconColorClass: string;
  iconBgColorClass: string;
  borderColorClass: string;
  isSpinning: boolean;
}

interface DimensionItem {
  section: 'supported' | 'required';
  source: string;
  key: string;
  value: string;
  keyLower: string; // Cached lowercased key for searching
  valueLower: string; // Cached lowercased value for searching
}

/**
 * Component for displaying an overview of a device, including its health,
 * dimensions, and other key information.
 */
@Component({
  selector: 'app-device-overview-tab',
  standalone: true,
  imports: [CommonModule, MatIconModule, InfoCard, FormsModule, OverviewPage],
  templateUrl: './device_overview_tab.ng.html',
  styleUrl: './device_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceOverviewTab implements OnInit, OnDestroy, OnChanges {
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  @Input({required: true}) device!: DeviceOverview;

  navList: NavItem[] = [
    {
      id: 'overview-health',
      label: 'Health & Activity',
    },
    {
      id: 'overview-system',
      label: 'Basic Information',
    },
    {
      id: 'overview-permissions',
      label: 'Permissions',
    },
    {
      id: 'overview-capabilities',
      label: 'Capabilities',
    },
    {
      id: 'overview-dimensions',
      label: 'Dimensions',
    },
    {
      id: 'overview-properties',
      label: 'Properties',
    },
  ];

  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;

  flatDimensions: DimensionItem[] = [];
  filteredDimensions: DimensionItem[] = [];

  dimensionsSearchTerm = '';
  private readonly searchSubject = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  filteredDimensions$ = signal<DimensionItem[]>([]);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['device']) {
      // Reset active section when device changes, e.g., navigating between devices
    }
  }

  ngOnInit(): void {
    // Initialize flat dimensions
    this.initFlatDimensions();
    this.filteredDimensions = [...this.flatDimensions];

    this.searchSubject
      .pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe((searchTerm) => {
        const filtered = this.filterDimensions(searchTerm);
        this.filteredDimensions = [...filtered];
        this.changeDetectorRef.detectChanges();
      });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Map HealthState to UI properties
  getHealthStateUI(state: HealthState): HealthStateUI {
    switch (state) {
      case 'IN_SERVICE_IDLE':
        return {
          icon: 'check_circle',
          iconColorClass: 'text-green-600',
          iconBgColorClass: 'bg-green-100',
          borderColorClass: 'border-l-green-500',
          isSpinning: false,
        };
      case 'IN_SERVICE_BUSY':
        return {
          icon: 'sync',
          iconColorClass: 'text-blue-600',
          iconBgColorClass: 'bg-blue-100',
          borderColorClass: 'border-l-blue-500',
          isSpinning: true,
        };
      case 'OUT_OF_SERVICE_RECOVERING':
        return {
          icon: 'autorenew',
          iconColorClass: 'text-amber-600',
          iconBgColorClass: 'bg-amber-100',
          borderColorClass: 'border-l-amber-500',
          isSpinning: true,
        };
      case 'OUT_OF_SERVICE_TEMP_MAINT':
        return {
          icon: 'warning',
          iconColorClass: 'text-amber-600',
          iconBgColorClass: 'bg-amber-100',
          borderColorClass: 'border-l-amber-500',
          isSpinning: false,
        };
      case 'IDLE_BUT_QUARANTINED':
        return {
          icon: 'block',
          iconColorClass: 'text-red-600',
          iconBgColorClass: 'bg-red-100',
          borderColorClass: 'border-l-red-500',
          isSpinning: false,
        };
      case 'OUT_OF_SERVICE_NEEDS_FIXING':
      default:
        return {
          icon: 'error',
          iconColorClass: 'text-red-600',
          iconBgColorClass: 'bg-red-100',
          borderColorClass: 'border-l-red-500',
          isSpinning: false,
        };
    }
  }

  getWifiSignalIcon(rssi: number | undefined): string {
    if (rssi === undefined) return '';
    if (rssi >= -67) return 'signal_wifi_4_bar';
    if (rssi >= -75) return 'network_wifi_3_bar';
    if (rssi >= -85) return 'network_wifi_2_bar';
    return 'network_wifi_1_bar';
  }

  getWifiQualityText(rssi: number | undefined): string {
    if (rssi === undefined) return '';
    if (rssi >= -67) return 'Excellent';
    if (rssi >= -75) return 'Good';
    if (rssi >= -85) return 'Okay';
    return 'Weak';
  }

  // Dimension related functions
  private initFlatDimensions(): void {
    this.flatDimensions = [];
    this.flattenDimensions(this.device.dimensions.supported, 'supported');
    this.flattenDimensions(this.device.dimensions.required, 'required');
  }

  private flattenDimensions(
    items: Record<string, DimensionSourceGroup> | undefined,
    section: 'supported' | 'required',
  ): void {
    if (!items) return;

    const sectionItems = Object.entries(items).flatMap(([source, group]) =>
      (group.dimensions || []).map((dimension) => {
        const key = dimension.name || '';
        const value = dimension.value || '';
        return {
          section,
          source,
          key,
          value,
          keyLower: key.toLowerCase(),
          valueLower: value.toLowerCase(),
        };
      }),
    );
    this.flatDimensions.push(...sectionItems);
  }

  private filterDimensions(searchTerm: string): DimensionItem[] {
    const searchTermLower = searchTerm.toLowerCase().trim();

    if (!searchTermLower) return this.flatDimensions;
    return this.flatDimensions.filter(
      (item) =>
        item.keyLower.includes(searchTermLower) ||
        item.valueLower.includes(searchTermLower),
    );
  }

  getGroupedData(
    section: 'supported' | 'required',
  ): Record<string, DimensionItem[]> {
    const grouped = this.filteredDimensions
      .filter((item) => item.section === section)
      .reduce(
        (groups, item) => {
          const groupKey = item.source;
          groups[groupKey] = groups[groupKey] || [];
          groups[groupKey].push(item);
          return groups;
        },
        {} as Record<string, DimensionItem[]>,
      );
    return grouped;
  }

  trackByKeyAndValue(index: number, item: DimensionItem): string {
    return `${item.key}-${item.value}-${index}`;
  }

  onDimensionsSearchChange(): void {
    this.searchSubject.next(this.dimensionsSearchTerm);
  }
}
