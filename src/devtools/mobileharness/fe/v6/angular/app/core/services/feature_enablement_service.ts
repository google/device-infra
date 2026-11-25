import {Injectable, inject} from '@angular/core';

import {
  CommonParamsService,
  CommonQueryParamKey,
} from './common_params_service';
import {Environment} from './environment';

/**
 * Names of the comma-separated `force_ready` common query parameter that this
 * app knows. The names are trimmed and case-insensitive; other names (e.g.
 * `search`, used by the ATS page embedding this app) are ignored here.
 */
enum ForceReadyFeature {
  /**
   * The device configuration features: the bulk Wi-Fi / dimension
   * configuration actions, and the host / device Configuration buttons.
   */
  CONFIG = 'config',
}

/**
 * Service answering whether a frontend feature is enabled for the current
 * session, so that components only ask "is feature X on?" instead of
 * interpreting raw query parameters themselves.
 *
 * Features that are still under development are hidden from end users and
 * opened to developers through the `force_ready` common query parameter
 * captured by {@link CommonParamsService}, e.g. `force_ready=config`;
 * `force_all_ready` is the global switch implying every feature. Like the
 * common query parameters, the answers are fixed for the SPA's lifetime (until
 * a full page reload). Graduating a feature to general availability only
 * requires its method here to stop consulting the flags, for every build or
 * only for the Google-internal one.
 */
@Injectable({providedIn: 'root'})
export class FeatureEnablementService {
  private readonly commonParamsService = inject(CommonParamsService);
  private readonly environment = inject(Environment);

  /** Names listed in `force_ready`, captured once for the SPA's lifetime. */
  private readonly forceReadyNames: ReadonlySet<string> = new Set(
    (this.commonParamsService.getParam('force_ready') ?? '')
      .split(',')
      .map((name) => name.trim().toLowerCase()),
  );

  /** Whether the `force_all_ready` global switch is on, read once. */
  private readonly isForceAllReady = this.isFlagActive('force_all_ready');

  /**
   * Whether the bulk Wi-Fi configuration action (Configure Wi-Fi for the
   * selected devices, on the device search page and in the Devices table of
   * the host detail page) is enabled: always in the Google-internal build,
   * where it's launched; with `force_ready=config` in the OSS build.
   */
  isBulkWifiConfigEnabled(): boolean {
    return (
      this.environment.isGoogleInternal() ||
      this.isForceReady(ForceReadyFeature.CONFIG)
    );
  }

  /**
   * Whether the bulk dimension configuration action (the Configure menu for
   * the selected devices, in the same places as the Wi-Fi one) is enabled:
   * always in the Google-internal build, where it's launched; with
   * `force_ready=config` in the OSS build.
   */
  isBulkDimensionConfigEnabled(): boolean {
    return (
      this.environment.isGoogleInternal() ||
      this.isForceReady(ForceReadyFeature.CONFIG)
    );
  }

  /**
   * Whether the Configuration button of the host detail page is enabled:
   * always in the Google-internal build, where the backend decides; with
   * `force_ready=config` in the OSS build.
   */
  isHostConfigEnabled(): boolean {
    return (
      this.environment.isGoogleInternal() ||
      this.isForceReady(ForceReadyFeature.CONFIG)
    );
  }

  /**
   * Whether the device Configuration action is enabled, i.e. the Configuration
   * button of the device detail page and the per-device Configuration action
   * in the Devices table of the host detail page: always in the
   * Google-internal build, where the backend decides; with
   * `force_ready=config` in the OSS build.
   */
  isDeviceConfigEnabled(): boolean {
    return (
      this.environment.isGoogleInternal() ||
      this.isForceReady(ForceReadyFeature.CONFIG)
    );
  }

  /**
   * Whether `force_ready` lists the given feature, or the `force_all_ready`
   * global switch is on.
   */
  private isForceReady(feature: ForceReadyFeature): boolean {
    return this.isForceAllReady || this.forceReadyNames.has(feature);
  }

  /**
   * A flag is active when present with any value other than '', '0' or
   * 'false' (case-insensitive), e.g. `=true` or `=1`. This mirrors how
   * `force_all_ready` is interpreted by the forceReadyInterceptor.
   */
  private isFlagActive(key: CommonQueryParamKey): boolean {
    const val = this.commonParamsService.getParam(key);
    return val !== null && !['', '0', 'false'].includes(val.toLowerCase());
  }
}
