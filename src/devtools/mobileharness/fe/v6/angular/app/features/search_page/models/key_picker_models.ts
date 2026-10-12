import {
  Filter,
  Fleet,
  FleetFilterChipMetadata,
  SearchEntity,
} from '../../../core/models/search';

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

/** View model representation of a single entry in the key picker catalog. */
export interface KeyPickerEntryView {
  key: string;
  name: string;
  meta: string;
  applied: boolean;
  inert: boolean;
  filterMetadata?: FleetFilterChipMetadata;
}

/** Section with entries for rendering either filter-key or group-by-key catalogs generically. */
export interface KeyPickerSectionView {
  heading: string;
  entries: KeyPickerEntryView[];
  totalAvailable?: number;
  hasMore?: boolean;
}
