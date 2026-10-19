import {DatePipe, DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  OnInit,
} from '@angular/core';
import {rxResource} from '@angular/core/rxjs-interop';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {GlobalSummary} from '@deviceinfra/app/core/models/home';
import {HOME_SERVICE} from '@deviceinfra/app/core/services/home';
import {BreakdownTable} from '@deviceinfra/app/features/home/components/breakdown_table';
import {UtilizationBar} from '@deviceinfra/app/features/home/components/utilization_bar';
import {BreakdownTableRow} from '@deviceinfra/app/features/home/models';
import {DrilldownRoutePipe} from '@deviceinfra/app/features/home/utils';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';

/**
 * Home page displaying the OmniLab Global Fleet Summary card.
 */
@Component({
  selector: 'app-home-page',
  standalone: true,
  templateUrl: './home_page.ng.html',
  styleUrl: './home_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    RouterLink,
    BreakdownTable,
    UtilizationBar,
    DrilldownRoutePipe,
  ],
})
export class HomePage implements OnInit {
  private readonly homeService = inject(HOME_SERVICE);
  private readonly loadingService = inject(LoadingService);

  // Declarative reactive resource managing global summary data and async state
  readonly summaryResource = rxResource<GlobalSummary | null, void>({
    stream: () => this.homeService.getGlobalSummary(),
  });

  // Track the timestamp of successful data resolution declaratively without imperative effects
  readonly lastUpdated = linkedSignal({
    source: () => this.summaryResource.status(),
    computation: (status, previous?: {value: Date | null}): Date | null => {
      if (status === 'resolved') {
        return new Date();
      }
      return previous?.value ?? null;
    },
  });

  // Safely guard with hasValue(): Angular Resource.value() throws ResourceValueError in error state
  readonly summary = computed<GlobalSummary | null>(() => {
    if (this.summaryResource.hasValue()) {
      return this.summaryResource.value() ?? null;
    }
    return null;
  });
  readonly isLoading = this.summaryResource.isLoading;
  readonly errorMessage = computed<string | null>(() => {
    const err = this.summaryResource.error();
    if (!err) return null;
    return (
      (err as {message?: string})?.message ||
      'Failed to load OmniLab summary data.'
    );
  });

  readonly isRefreshing = computed(
    () => this.summaryResource.isLoading() && this.summaryResource.hasValue(),
  );

  readonly labTypeRows = computed<readonly BreakdownTableRow[]>(() => {
    const items = this.summary()?.firstParty?.labTypes ?? [];
    return items.map((item) => ({
      id: item.id,
      name: item.displayName,
      subtitle: item.description,
      dotClass: `dot-${item.id}`,
      hosts: item.hosts,
      devices: item.devices,
      utilization: item.utilization,
    }));
  });

  readonly partnerLabRows = computed<readonly BreakdownTableRow[]>(() => {
    const labs = this.summary()?.partnerAtsLabs?.labs ?? [];
    return labs.map((lab) => ({
      id: lab.controllerId,
      name: lab.displayName || lab.controllerId,
      dotClass: 'dot-partner',
      hosts: lab.hosts,
      devices: lab.devices,
      utilization: lab.utilization,
    }));
  });

  ngOnInit() {
    this.loadingService.hide();
  }

  refresh() {
    this.summaryResource.reload();
  }
}
