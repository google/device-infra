import {Filter, FleetColumnDescriptor} from '../../../core/models/search';
import {EntityType} from './search_page_ui';

/** Dialog configuration payload for ColumnSelectorComponent. */
export interface ColumnSelectorDialogData {
  entity: EntityType;
  fleet: string;
  columns?: FleetColumnDescriptor[];
  defaultColumns?: FleetColumnDescriptor[];
  activeFilters?: Filter[];
}

/** Dialog output payload on apply. */
export interface ColumnSelectorResult {
  columns: FleetColumnDescriptor[];
  isReset?: boolean;
}
