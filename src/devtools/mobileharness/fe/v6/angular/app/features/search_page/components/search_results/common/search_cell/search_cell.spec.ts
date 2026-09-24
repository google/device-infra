import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Cell, Column, Indicator} from '../../../../../../core/models/search';
import {UrlService} from '../../../../../../core/services/url_service';
import {SearchCellComponent} from './search_cell';

@Component({
  standalone: true,
  imports: [SearchCellComponent],
  template: `
    <app-search-cell [cell]="cell" [column]="column"></app-search-cell>
  `,
})
class TestHostComponent {
  cell: Cell | null | undefined = null;
  column: Column | undefined = undefined;
}

describe('SearchCellComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let urlService: UrlService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [provideNoopAnimations(), provideRouter([])],
    }).compileComponents();

    urlService = TestBed.inject(UrlService);
    spyOn(urlService, 'notifyNavigated');

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
  });

  it('should render empty dash when cell is null or empty', () => {
    host.cell = null;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent?.trim()).toBe('-');
  });

  it('should render plain text with truncation tooltip', () => {
    host.cell = {text: {value: 'Pixel 9 Pro XL'}};
    host.column = {key: 'model', displayName: 'Model'};
    fixture.detectChanges();

    const span = fixture.nativeElement.querySelector('.cell-text');
    expect(span).toBeTruthy();
    expect(span.textContent.trim()).toBe('Pixel 9 Pro XL');
  });

  it('should render link cell with routerLink and truncation tooltip', () => {
    host.cell = {
      link: {
        text: 'device-12345',
        target: {device: {id: 'device-12345', hostName: 'host1', universe: 'vivo'}},
      },
    };
    host.column = {key: 'id', displayName: 'Device ID'};
    fixture.detectChanges();

    const anchor = fixture.nativeElement.querySelector('a.device-link') as HTMLAnchorElement;
    expect(anchor).toBeTruthy();
    expect(anchor.textContent.trim()).toBe('device-12345');
    expect(anchor.getAttribute('href')).toBe('/devices/device-12345?host_name=host1&universe=vivo');
  });

  it('should render status cell with status dot and text', () => {
    host.cell = {
      status: {text: 'IDLE', indicator: Indicator.INDICATOR_OK},
    };
    host.column = {key: 'status', displayName: 'Status'};
    fixture.detectChanges();

    const statusCell = fixture.nativeElement.querySelector('.status-cell');
    expect(statusCell).toBeTruthy();
    const dot = statusCell.querySelector('.rt-dot');
    expect(dot).toBeTruthy();
    expect(dot.classList).toContain('status-ok');
    expect(statusCell.querySelector('.status-text').textContent.trim()).toBe(
      'IDLE',
    );
  });

  it('should render chips cell with overflow chip list', () => {
    host.cell = {
      chips: {values: ['wifi', 'bluetooth', 'nfc']},
    };
    host.column = {key: 'features', displayName: 'Features'};
    fixture.detectChanges();

    const chipList = fixture.nativeElement.querySelector(
      'app-overflow-chip-list',
    );
    expect(chipList).toBeTruthy();
  });

  it('should render multilink cell as multilink-list with individual links', () => {
    host.cell = {
      multiLink: {
        entries: [
          {text: 'Host A', target: {host: {hostName: 'host-a'}}},
          {text: 'Host B', target: {host: {hostName: 'host-b'}}},
        ],
      },
    };
    host.column = {key: 'hosts', displayName: 'Hosts'};
    fixture.detectChanges();

    const list = fixture.nativeElement.querySelector('.multilink-list');
    expect(list).toBeTruthy();
    const links = fixture.nativeElement.querySelectorAll('a.device-link');
    expect(links.length).toBe(2);
    expect(links[0].textContent.trim()).toBe('Host A');
    expect(links[0].getAttribute('href')).toBe('/hosts/host-a');
    expect(links[1].textContent.trim()).toBe('Host B');
    expect(links[1].getAttribute('href')).toBe('/hosts/host-b');
  });

  it('should notify UrlService when device link is clicked', () => {
    host.cell = {
      link: {
        text: 'device-12345',
        target: {device: {id: 'device-12345'}},
      },
    };
    host.column = {key: 'id', displayName: 'Device ID'};
    fixture.detectChanges();

    const anchor = fixture.nativeElement.querySelector(
      'a.device-link',
    ) as HTMLAnchorElement;
    anchor.click();

    expect(urlService.notifyNavigated).toHaveBeenCalledWith(
      'device_details',
      jasmine.objectContaining({
        'uuid': 'device-12345',
      }),
    );
  });

  it('should notify UrlService when host link is clicked', () => {
    host.cell = {
      link: {
        text: 'host-1',
        target: {host: {hostName: 'host-1'}},
      },
    };
    host.column = {key: 'host', displayName: 'Host'};
    fixture.detectChanges();

    const anchor = fixture.nativeElement.querySelector(
      'a.device-link',
    ) as HTMLAnchorElement;
    anchor.click();

    expect(urlService.notifyNavigated).toHaveBeenCalledWith(
      'host_details',
      jasmine.objectContaining({
        'host_name': 'host-1',
      }),
    );
  });
});
