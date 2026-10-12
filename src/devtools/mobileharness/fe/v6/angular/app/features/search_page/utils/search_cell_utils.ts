import {Indicator} from '../../../core/models/search';

/** Mapping of protobuf Indicator enum values to CSS status classes. */
const INDICATOR_CLASS_MAP: Record<string, string> = {
  [Indicator.INDICATOR_OK]: 'status-ok',
  [Indicator.INDICATOR_ACTIVE]: 'status-active',
  [Indicator.INDICATOR_ERROR]: 'status-error',
  [Indicator.INDICATOR_NEUTRAL]: 'status-neutral',
};

/** Computes the CSS status class name directly from protobuf Indicator. */
export function getStatusClass(indicator?: Indicator | string): string {
  return (indicator && INDICATOR_CLASS_MAP[indicator]) || 'status-neutral';
}
