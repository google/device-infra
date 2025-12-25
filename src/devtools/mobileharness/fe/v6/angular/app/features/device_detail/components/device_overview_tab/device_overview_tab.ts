import {BreakpointObserver} from '@angular/cdk/layout';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  signal,
  SimpleChanges,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged, takeUntil} from 'rxjs/operators';
import type {DeviceOverview} from '../../../../core/models/device_overview';
import {
  HealthState,
  type DeviceDimension,
  type DimensionSourceGroup,
  type SubDeviceInfo,
} from '../../../../core/models/device_overview';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {InfoCard} from '../../../../shared/components/info_card/info_card';
import {
  NavItem,
  OverviewPage,
} from '../../../../shared/components/overview_page/overview_page';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {objectUtils} from '../../../../shared/utils/object_utils';
import {TestbedConfigViewer} from '../testbed_config_viewer/testbed_config_viewer';

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
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatDialogModule,
    OverviewPage,
    InfoCard,
  ],
  templateUrl: './device_overview_tab.ng.html',
  styleUrl: './device_overview_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceOverviewTab implements OnInit, OnDestroy, OnChanges {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly dialog = inject(MatDialog);
  private readonly deviceService = inject(DEVICE_SERVICE);

  @Input({required: true}) device!: DeviceOverview;

  navList: NavItem[] = [];

  readonly objectUtils = objectUtils;
  readonly dateUtils = dateUtils;

  // Dimensions filtering
  dimensionsSearchTerm = '';
  private readonly searchSubject = new Subject<string>();

  flatDimensions: DimensionItem[] = [];
  filteredDimensions = signal<DimensionItem[]>([]);

  readonly groupedSupportedDimensions = computed(() =>
    this.getGroupedData('supported'),
  );
  readonly groupedRequiredDimensions = computed(() =>
    this.getGroupedData('required'),
  );

  // Capabilities filtering
  driversSearchTerm = '';
  decoratorsSearchTerm = '';
  private readonly driversSearchSubject = new Subject<string>();
  private readonly decoratorsSearchSubject = new Subject<string>();
  filteredDrivers = signal<string[]>([]);
  filteredDecorators = signal<string[]>([]);

  isSubDeviceCollapsible = signal(true);

  // Sub-device filtering
  subDeviceDimensionSearchTerms: Record<string, string> = {};

  private readonly destroy$ = new Subject<void>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['device'] && this.device) {
      this.navList = this.getNavList();

      this.initCapabilities();
      this.initFlatDimensions();
      this.filteredDimensions.set(
        this.filterDimensions(this.dimensionsSearchTerm),
      );

      this.updateCollapsibleBasedOnScreenWidth(
        this.breakpointObserver.isMatched('(min-width: 1440px)'),
      );
    }
  }

  ngOnInit(): void {
    this.navList = this.getNavList();

    this.searchSubject
      .pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe((searchTerm) => {
        const filtered = this.filterDimensions(searchTerm);
        this.filteredDimensions.set([...filtered]);
      });

    this.driversSearchSubject
      .pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe((searchTerm) => {
        this.filterDrivers(searchTerm);
      });

    this.decoratorsSearchSubject
      .pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe((searchTerm) => {
        this.filterDecorators(searchTerm);
      });

    this.breakpointObserver
      .observe('(min-width: 1440px)')
      .pipe(takeUntil(this.destroy$))
      .subscribe((result) => {
        this.updateCollapsibleBasedOnScreenWidth(result.matches);
      });
  }

  updateCollapsibleBasedOnScreenWidth(isWide: boolean): void {
    if (
      this.device?.subDevices &&
      this.device.subDevices.length > 0 &&
      this.device.subDevices.length % 2 === 0
    ) {
      this.isSubDeviceCollapsible.set(!isWide);
    } else {
      this.isSubDeviceCollapsible.set(true);
    }
  }

  getNavList(): NavItem[] {
    const list: NavItem[] = [
      {
        id: 'overview-health',
        label: 'Health & Activity',
      },
      {
        id: 'overview-system',
        label: 'Basic Information',
      },
    ];
    // Add sub-devices nav item if there are sub-devices.
    if (this.device.subDevices && this.device.subDevices.length > 0) {
      list.push({
        id: 'overview-subdevices',
        label: 'Sub-Devices',
      });
    }

    list.push(
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
    );
    return list;
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
    this.driversSearchSubject.complete();
    this.decoratorsSearchSubject.complete();
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
    const grouped = this.filteredDimensions()
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

  // Capabilities related functions
  private initCapabilities(): void {
    if (this.device && this.device.capabilities) {
      // Re-apply filter if search term exists
      if (this.driversSearchTerm) {
        this.filterDrivers(this.driversSearchTerm);
      } else {
        this.filteredDrivers.set([
          ...this.device.capabilities.supportedDrivers,
        ]);
      }

      if (this.decoratorsSearchTerm) {
        this.filterDecorators(this.decoratorsSearchTerm);
      } else {
        this.filteredDecorators.set([
          ...this.device.capabilities.supportedDecorators,
        ]);
      }
    }
  }

  private filterDrivers(searchTerm: string): void {
    const lowerTerm = searchTerm.toLowerCase();
    if (!this.device || !this.device.capabilities) return;

    this.filteredDrivers.set(
      this.device.capabilities.supportedDrivers.filter((d) =>
        d.toLowerCase().includes(lowerTerm),
      ),
    );
  }

  private filterDecorators(searchTerm: string): void {
    const lowerTerm = searchTerm.toLowerCase();
    if (!this.device || !this.device.capabilities) return;

    this.filteredDecorators.set(
      this.device.capabilities.supportedDecorators.filter((d) =>
        d.toLowerCase().includes(lowerTerm),
      ),
    );
  }

  onDriversSearchChange(): void {
    this.driversSearchSubject.next(this.driversSearchTerm);
  }

  onDecoratorsSearchChange(): void {
    this.decoratorsSearchSubject.next(this.decoratorsSearchTerm);
  }

  showTestbedConfig(event: MouseEvent) {
    event.stopPropagation();
    this.deviceService.getTestbedConfig(this.device.id).subscribe((config) => {
      this.dialog.open(TestbedConfigViewer, {
        width: '800px',
        data: {
          deviceId: this.device.id,
          yamlContent: config.yamlContent,
          codeSearchLink: config.codeSearchLink,
        },
        autoFocus: false,
        panelClass: 'testbed-config-dialog-panel',
      });
    });
  }

  getFilteredSubDeviceDimensions(sub: SubDeviceInfo): DeviceDimension[] {
    const term =
      this.subDeviceDimensionSearchTerms[sub.id]?.toLowerCase() || '';
    const dims = sub.dimensions || [];
    if (!term) return dims;
    return dims.filter(
      (d) =>
        (d.name?.toLowerCase() || '').includes(term) ||
        (d.value?.toLowerCase() || '').includes(term),
    );
  }
}
