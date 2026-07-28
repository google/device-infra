import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component} from '@angular/core';

/**
 * Component to display login required / session expired info in a dialog.
 */
@Component({
  selector: 'app-login-required-content',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './login_required_content.ng.html',
  styleUrls: ['./login_required_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginRequiredContent {}
