import {Filter, Fleet, SearchEntity} from '../../../core/models/search';

/** Mode of key picker: selecting a filter key or a group-by key. */
export type KeyPickerMode = 'filter' | 'groupby';

/** Configuration payload for KeyPickerComponent dialog. */
export interface KeyPickerDialogData {
  mode: KeyPickerMode;
  entity?: SearchEntity;
  fleet?: Fleet;
  activeFilters?: Filter[];
  activeGroupBy?: string[];
}
