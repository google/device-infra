import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {RouterLink} from '@angular/router';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {
  MOCK_DEVICE_SCENARIOS,
  MOCK_HOST_SCENARIOS,
  MOCK_TEST_SCENARIOS,
} from '../../core/services/mock_data';
import {
  MockDeviceScenario,
  MockHostScenario,
  MockTestScenario,
} from '../../core/services/mock_data/models';

/**
 * Component for displaying a harness of development devices and their scenarios.
 * This page uses mock data to showcase different device states and configurations.
 */
@Component({
  selector: 'app-dev-harness-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  templateUrl: './dev_harness_page.ng.html',
  styleUrl: './dev_harness_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevHarnessPage {
  readonly deviceScenarios: MockDeviceScenario[];
  readonly hostScenarios: MockHostScenario[];
  readonly testScenarios: MockTestScenario[];

  readonly deviceCollapsed = signal(false);
  readonly hostCollapsed = signal(false);
  readonly testCollapsed = signal(false);

  private readonly loadingService = inject(LoadingService);

  constructor() {
    this.deviceScenarios = [...MOCK_DEVICE_SCENARIOS].sort((a, b) => {
      const aNum = Number(a.scenarioName.split('.')[0]);
      const bNum = Number(b.scenarioName.split('.')[0]);
      return aNum - bNum;
    });

    this.hostScenarios = [...MOCK_HOST_SCENARIOS].sort((a, b) => {
      const aName = a.scenarioName;
      const bName = b.scenarioName;
      if (aName < bName) return -1;
      if (aName > bName) return 1;
      return 0;
    });

    this.testScenarios = [...MOCK_TEST_SCENARIOS].sort((a, b) => {
      const aName = a.scenarioName;
      const bName = b.scenarioName;
      if (aName < bName) return -1;
      if (aName > bName) return 1;
      return 0;
    });

    this.loadingService.hide();
  }

  toggleDeviceScenarios() {
    this.deviceCollapsed.update((v) => !v);
  }

  toggleHostScenarios() {
    this.hostCollapsed.update((v) => !v);
  }

  toggleTestScenarios() {
    this.testCollapsed.update((v) => !v);
  }
}
