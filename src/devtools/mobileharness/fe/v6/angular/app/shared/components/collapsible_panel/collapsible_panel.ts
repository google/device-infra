import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component for displaying a collapsible panel with a title and content.
 * It can be expanded and collapsed by clicking the expand icon.
 */
@Component({
  selector: 'app-collapsible-panel',
  standalone: true,
  templateUrl: './collapsible_panel.ng.html',
  styleUrl: './collapsible_panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule],
})
export class CollapsiblePanel implements OnInit {
  @Input() expanded = true;
  @Input() title = '';

  ngOnInit() {}

  toggle(): void {
    this.expanded = !this.expanded;
  }
}
