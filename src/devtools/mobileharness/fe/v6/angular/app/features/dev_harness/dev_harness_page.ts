import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterLink} from '@angular/router';
import {
  MOCK_DEVICE_SCENARIOS,
  MOCK_HOST_SCENARIOS,
} from '../../core/services/mock_data';
import {
  MockDeviceScenario,
  MockHostScenario,
} from '../../core/services/mock_data/models';

/**
 * Component for displaying a harness of development devices and their scenarios.
 * This page uses mock data to showcase different device states and configurations.
 */
@Component({
  selector: 'app-dev-harness-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dev_harness_page.ng.html',
  styleUrl: './dev_harness_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevHarnessPage {
  readonly deviceScenarios: MockDeviceScenario[];
  readonly hostScenarios: MockHostScenario[];

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
  }
}
