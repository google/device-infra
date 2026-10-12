import {Filter, FleetColumnDescriptor} from '../../../core/models/search';
import {EntityType, FleetScope} from './search_page_ui';

/** Dialog configuration payload for ColumnSelectorComponent. */
export interface ColumnSelectorDialogData {
  entity: EntityType;
  fleet: FleetScope;
  columns?: FleetColumnDescriptor[];
  defaultColumns?: FleetColumnDescriptor[];
  activeFilters?: Filter[];
}

/** Dialog output payload on apply. */
export interface ColumnSelectorResult {
  columns: FleetColumnDescriptor[];
  isReset?: boolean;
}
