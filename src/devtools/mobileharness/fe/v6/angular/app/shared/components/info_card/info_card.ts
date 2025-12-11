import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component for displaying a collapsible panel with a title and content.
 * It can be expanded and collapsed by clicking the expand icon.
 */
@Component({
  selector: 'app-info-card',
  standalone: true,
  templateUrl: './info_card.ng.html',
  styleUrl: './info_card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule],
  host: {'class': 'info-card'},
})
export class InfoCard implements OnInit {
  @Input() expanded = true;
  @Input() title = '';
  @Input() collapsible = true;

  ngOnInit() {}

  toggle(): void {
    if (!this.collapsible) return;
    this.expanded = !this.expanded;
  }
}
