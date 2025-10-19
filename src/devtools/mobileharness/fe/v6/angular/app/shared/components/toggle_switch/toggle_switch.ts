import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  forwardRef,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {ControlValueAccessor, NG_VALUE_ACCESSOR} from '@angular/forms';

/** Component for toggle switch. */
@Component({
  selector: 'toggle-switch',
  standalone: true,
  templateUrl: './toggle_switch.ng.html',
  styleUrl: './toggle_switch.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ToggleSwitch),
      multi: true,
    },
  ],
})
export class ToggleSwitch implements OnInit, ControlValueAccessor {
  @Input() checked = false;
  @Input() disabled = false;
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  @Input() color = '#007bff';
  @Input() label = '';
  @Input() labelPosition: 'left' | 'right' = 'right';

  @Output() readonly checkedChange = new EventEmitter<boolean>();
  @Output() readonly toggle = new EventEmitter<boolean>();

  // ControlValueAccessor properties
  private onChange = (value: boolean) => {};
  private onTouched = () => {};
  private isFormControl = false;

  ngOnInit() {
    // Component initialization logic
  }

  // ControlValueAccessor implementation
  writeValue(value: boolean): void {
    this.checked = value;
    this.isFormControl = true;
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  onToggle() {
    if (this.disabled) {
      return;
    }

    this.checked = !this.checked;

    // 如果是表单控件，调用表单回调
    if (this.isFormControl) {
      this.onChange(this.checked);
      this.onTouched();
    }

    this.checkedChange.emit(this.checked);
    this.toggle.emit(this.checked);
  }

  get toggleClasses() {
    return {
      'toggle-switch': true,
      [`toggle-switch--${this.size}`]: true,
      'toggle-switch--checked': this.checked,
      'toggle-switch--disabled': this.disabled,
    };
  }
}
