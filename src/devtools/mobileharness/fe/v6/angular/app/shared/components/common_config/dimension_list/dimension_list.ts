import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
// import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import type {DeviceDimension} from '../../../../../app/core/models/device_config_models';

/**
 * The component for displaying a list of dimensions.
 *
 * It is used in the common config page to display a list of dimensions.
 */
@Component({
  selector: 'app-dimension-list',
  standalone: true,
  templateUrl: './dimension_list.ng.html',
  styleUrl: './dimension_list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatInputModule,
    FormsModule,
    ReactiveFormsModule,
  ],
})
export class DimensionList implements OnInit {
  @Input() dimensions: DeviceDimension[] = [];
  @Output() readonly dimensionsChange = new EventEmitter<DeviceDimension[]>();
  @Output() readonly hasError = new EventEmitter<boolean>();

  currentEditingIndex: number | null = null;

  ngOnInit() {}

  add() {
    this.dimensions.push({name: `new_dimension_${Date.now()}`, value: ''});
    this.currentEditingIndex = this.dimensions.length - 1;
  }

  remove(index: number) {
    this.dimensions.splice(index, 1);
    if (this.currentEditingIndex === index) {
      this.currentEditingIndex = null;
    }
  }

  onDimensionChanged(index: number, name: string, value: string) {
    this.currentEditingIndex = index;
    this.dimensions[index].name = name;
    this.dimensions[index].value = value;

    this.hasError.emit(
      this.dimensions.some((dimension, i) => this.duplicate(i)),
    );
  }

  duplicate(index: number): boolean {
    return (
      this.dimensions.some(
        (dimension, i) =>
          i !== index &&
          dimension.name === this.dimensions[index].name &&
          dimension.value === this.dimensions[index].value,
      ) ?? false
    );
  }
}
