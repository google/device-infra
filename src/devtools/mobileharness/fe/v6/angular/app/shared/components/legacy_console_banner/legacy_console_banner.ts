import {CommonModule} from '@angular/common';
import {Component, computed, inject, input} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {ActivatedRoute} from '@angular/router';
import {map} from 'rxjs/operators';
import {EnvUniverseService} from '../../../core/services/env_universe_service';

/**
 * Banner component to link back to the legacy console.
 * Only shows in standalone mode and for Google 1P universe.
 */
@Component({
  selector: 'app-legacy-console-banner',
  templateUrl: './legacy_console_banner.ng.html',
  styleUrl: './legacy_console_banner.scss',
  standalone: true,
  imports: [CommonModule, MatIconModule],
})
export class LegacyConsoleBanner {
  private readonly route = inject(ActivatedRoute);
  private readonly envUniverseService = inject(EnvUniverseService);

  readonly legacyUrl = input.required<string>();
  readonly bannerType = input.required<
    'host' | 'device' | 'session' | 'job' | 'test'
  >();

  private readonly isStandalone = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => params.get('is_embedded_mode') !== 'true'),
    ),
    {initialValue: true},
  );

  readonly shouldShow = computed(() => {
    return (
      this.isStandalone() &&
      this.envUniverseService.isGoogle1P() &&
      !!this.legacyUrl()
    );
  });
}
