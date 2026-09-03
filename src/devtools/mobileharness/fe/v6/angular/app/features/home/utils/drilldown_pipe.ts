/**
 * @fileoverview Pure pipe converting FleetSearchTarget into Angular Router drilldown parameters.
 */

import {Pipe, PipeTransform} from '@angular/core';
import type {FleetSearchTarget} from '@deviceinfra/app/core/models/home';
import {buildDrilldownUrl, DrilldownTarget} from './drilldown_utils';

/**
 * Pure pipe to build drilldown target route and query params from a FleetSearchTarget.
 * Pure memoization ensures calculations run only when the search target changes.
 */
@Pipe({
  name: 'drilldownRoute',
  standalone: true,
  pure: true,
})
export class DrilldownRoutePipe implements PipeTransform {
  transform(target?: FleetSearchTarget | null): DrilldownTarget | null {
    if (!target) return null;
    return buildDrilldownUrl(target);
  }
}
