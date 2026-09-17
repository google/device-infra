import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component} from '@angular/core';

/** Root page component for OmniLab Assistant. */
@Component({
  selector: 'app-assistant-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './assistant_page.ng.html',
  styleUrls: ['./assistant_page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantPage {}
