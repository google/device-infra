import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {Dialog} from '../../../../shared/components/config_common/dialog/dialog';
import {openCodeSearch} from '../../../../shared/utils/safe_dom';

/** Data for TestbedConfigViewer dialog. */
export interface TestbedConfigViewerData {
  deviceId: string;
  yamlContent: string;
  codeSearchLink: string;
}

/**
 * Dialog component for viewing testbed configuration in YAML format.
 */
@Component({
  selector: 'app-testbed-config-viewer',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    Dialog,
  ],
  templateUrl: './testbed_config_viewer.ng.html',
  styleUrl: './testbed_config_viewer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestbedConfigViewer implements OnInit {
  dialogRef = inject(MatDialogRef<TestbedConfigViewer>);
  data: TestbedConfigViewerData = inject(MAT_DIALOG_DATA);

  lines: string[] = [];

  ngOnInit() {
    this.lines = this.data.yamlContent.split('\n');
  }

  openInCodeSearch() {
    openCodeSearch(this.data.codeSearchLink);
  }
}
