import {Injectable, inject} from '@angular/core';

import {
  CommonParamsService,
  CommonQueryParamKey,
} from './common_params_service';

/**
 * Service answering whether a frontend feature is enabled for the current
 * session, so that components only ask "is feature X on?" instead of
 * interpreting raw query parameters themselves.
 *
 * Features that are still under development are hidden from end users and
 * opened to developers through the `force_*_ready` common query parameters
 * captured by {@link CommonParamsService}; `force_all_ready` is the master
 * switch implying every feature-specific flag. Graduating a feature to general
 * availability only requires its method here to stop consulting the flags.
 */
@Injectable({providedIn: 'root'})
export class FeatureEnablementService {
  private readonly commonParamsService = inject(CommonParamsService);

  /**
   * Whether the bulk device configuration actions (Configure Wi-Fi / Configure
   * Dimension on the device search page) are enabled. Developer-only for now:
   * requires `force_bulk_config_ready` (or `force_all_ready`).
   */
  isBulkConfigEnabled(): boolean {
    return this.isForceReady('force_bulk_config_ready');
  }

  /**
   * Whether the given `force_*_ready` developer flag is active, either
   * explicitly or through the `force_all_ready` master switch.
   */
  private isForceReady(key: CommonQueryParamKey): boolean {
    return this.isFlagActive(key) || this.isFlagActive('force_all_ready');
  }

  /**
   * A flag is active when present with any value other than 'false'
   * (case-insensitive), e.g. `=true` or `=1`. This mirrors how
   * `force_all_ready` is interpreted by the forceReadyInterceptor.
   */
  private isFlagActive(key: CommonQueryParamKey): boolean {
    const val = this.commonParamsService.getParam(key);
    return val !== null && val.toLowerCase() !== 'false';
  }
}
