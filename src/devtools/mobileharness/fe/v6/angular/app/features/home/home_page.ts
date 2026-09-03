import {DatePipe, DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
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
import {AtsBreakdownTable} from '@deviceinfra/app/features/home/components/ats_breakdown_table';
import {UtilizationBar} from '@deviceinfra/app/features/home/components/utilization_bar';
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
    AtsBreakdownTable,
    UtilizationBar,
    DrilldownRoutePipe,
  ],
})
export class HomePage implements OnInit {
  private readonly homeService = inject(HOME_SERVICE);
  private readonly loadingService = inject(LoadingService);

  readonly lastUpdated = signal<Date | null>(null);

  constructor() {
    effect(() => {
      // Whenever data resolves successfully, update the timestamp
      if (this.summaryResource.status() === 'resolved') {
        this.lastUpdated.set(new Date());
      }
    });
  }

  // Declarative reactive resource managing global summary data and async state
  readonly summaryResource = rxResource<GlobalSummary | null, void>({
    stream: () => this.homeService.getGlobalSummary(),
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

  ngOnInit() {
    this.loadingService.hide();
  }

  refresh() {
    this.summaryResource.reload();
  }
}
