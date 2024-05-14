import {CommonModule} from '@angular/common';
import {NgModule} from '@angular/core';

import {HelloDeviceInfra} from './hello_device_infra';

@NgModule({
  imports: [CommonModule],
  declarations: [HelloDeviceInfra],
  exports: [HelloDeviceInfra],
})
export class HelloDeviceInfraModule {
}
