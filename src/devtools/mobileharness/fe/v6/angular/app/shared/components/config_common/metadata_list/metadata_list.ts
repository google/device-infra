import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
// import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';

import {SafeHtmlPipe} from '../../../pipes/safe_html_pipe';

/**
 * The column definition for the metadata list.
 */
export interface MetadataColumn {
  columnDef: string;
  header: string;
  cell: string;
  type: 'input'|'select'|'action';
  inputType?: 'text'|'number'|'email'|
      'password';  // Only used when type is 'input'.
  defaultValue?: string|
      (() => string);  // function default value is for generating default value
                       // with dynamic date.
  options?: string[];  // Only used when type is 'select'.
  placeholder?: string;
  required?: boolean;
}

declare interface EditabilityOverride {
  [index: number]: {editable: boolean; reason?: string};
}

/**
 * The UI status of the metadata list.
 */
export interface MetadataUiStatus {
  sectionStatus:
      {visible: boolean; editability?: {editable: boolean; reason?: string};};
  itemEditabilityOverrides?: EditabilityOverride;
}

/**
 * The component for displaying a list of metadataList.
 *
 * It is used in the common config page to display a list of metadataList.
 */
@Component({
  selector: 'app-metadata-list',
  standalone: true,
  templateUrl: './metadata_list.ng.html',
  styleUrl: './metadata_list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatIconModule,
    MatButtonModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    SafeHtmlPipe,
  ],
})
export class MetadataList<T extends Record<keyof T, string>> implements OnInit {
  @Input()
  uiStatus: MetadataUiStatus = {
    sectionStatus: {visible: true, editability: {editable: true, reason: ''}},
  };
  @Input()
  columns: MetadataColumn[] = [
    {
      columnDef: 'name',
      header: 'NAME',
      cell: 'name',
      type: 'input',
      inputType: 'text',
    },
    {
      columnDef: 'value',
      header: 'VALUE',
      cell: 'value',
      type: 'input',
      inputType: 'text',
    },
  ];
  @Input() emptyMessage = 'No metadata of this type have been added.';
  @Input() addButtonLabel = 'Add Metadata';
  @Input() metadataList: T[] = [];
  @Output() readonly metadataListChange = new EventEmitter<T[]>();
  @Output() readonly hasError = new EventEmitter<boolean>();

  readonly endColumns: string[] = ['action'];
  displayedColumns: string[] = [];

  errorMessage: string[] = [];

  ngOnInit() {
    this.columns = [...this.columns];
    this.displayedColumns = [
      ...this.columns.map((col) => col.columnDef),
      ...this.endColumns,
    ];

    this.validate();
  }

  add() {
    const newRow: T = {} as T;
    this.columns.forEach((column) => {
      if (column.defaultValue) {
        (newRow as Record<string, string>)[column.cell] =
            typeof column.defaultValue === 'function' ? column.defaultValue() :
                                                        column.defaultValue;
      } else {
        (newRow as Record<string, string>)[column.cell] = '';
      }
    });
    this.metadataList.push(newRow);

    this.onMetadataChanged();
  }

  remove(index: number) {
    this.metadataList.splice(index, 1);

    this.onMetadataChanged();

    // Update the item editability overrides.
    // e.g. if the removed item is the middle one, then the following items'
    // indexes should be decreased by 1.
    if (!this.uiStatus.itemEditabilityOverrides) return;
    const oldOverrides: EditabilityOverride =
        this.uiStatus.itemEditabilityOverrides;
    const newOverrides: EditabilityOverride = {};
    for (const key of Object.keys(oldOverrides)) {
      const numericKey = Number(key);
      if (numericKey < index) {
        newOverrides[numericKey] = oldOverrides[numericKey];
      } else if (numericKey > index) {
        newOverrides[numericKey - 1] = oldOverrides[numericKey];
      }
    }
    this.uiStatus.itemEditabilityOverrides = newOverrides;
  }

  onMetadataChanged() {
    this.metadataList = [...this.metadataList];
    this.metadataListChange.emit(this.metadataList);

    this.validate();
  }

  duplicate(index: number): boolean {
    // duplicate check for current item
    const item = this.metadataList[index];
    if (!item) return false;

    const currentItemEmpty = this.columns.every((col) => {
      return item[col.cell as keyof T] === '';
    });
    if (currentItemEmpty) return false;

    return this.metadataList.some((metadata, i) => {
      if (i === index) return false;

      return this.columns.every((col) => {
        return metadata[col.cell as keyof T] === item[col.cell as keyof T];
      });
    });
  }

  required(index: number): string {
    // required fields check for current item
    const requiredColumns = this.columns.filter((column) => column.required);
    if (requiredColumns.length === 0) return '';

    const missingFields = new Set<string>();
    const metadata = this.metadataList[index];
    requiredColumns.forEach((column) => {
      if (!metadata[column.cell as keyof T]) {
        missingFields.add(column.header);
      }
    });

    let missingFieldsError = '';
    if (missingFields.size > 0) {
      missingFieldsError = `${
          Array.from(missingFields).join(', ')} field(s) are required. <br />`;
    }

    return missingFieldsError;
  }

  validate() {
    this.errorMessage = new Array(this.metadataList.length).fill('');
    for (let i = 0; i < this.metadataList.length; i++) {
      const requiredError = this.required(i);
      if (requiredError) {
        this.errorMessage[i] = requiredError;
      }

      const duplicateError = this.duplicate(i);
      if (duplicateError) {
        this.errorMessage[i] +=
            'Duplicate data. Please remove one of the duplicates.';
      }
    }

    this.hasError.emit(this.errorMessage.some((error) => error !== ''));
  }

  showErrorRow = (index: number, row: T) => {
    return this.errorMessage[index] && this.errorMessage[index] !== '';
  };
}
