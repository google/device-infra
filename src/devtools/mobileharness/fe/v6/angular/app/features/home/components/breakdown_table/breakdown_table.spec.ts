import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {
  Fleet,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';
import {BreakdownTableRow} from '@deviceinfra/app/features/home/models';
import {BreakdownTable} from './breakdown_table';

describe('BreakdownTable Component', () => {
  let component: BreakdownTable;
  let fixture: ComponentFixture<BreakdownTable>;

  const mockRows: readonly BreakdownTableRow[] = [
    {
      id: 'core',
      name: 'Core Lab',
      subtitle: 'Shared pool, operated by OmniLab team',
      dotClass: 'dot-core',
      hosts: {
        count: 1420,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_HOST,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'host_field::lab_type',
              simple: {values: [{value: 'Core Lab'}]},
            },
          ],
        },
      },
      devices: {
        count: 48390,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'host_field::lab_type',
              simple: {values: [{value: 'Core Lab'}]},
            },
          ],
        },
      },
      utilization: {
        busy: {count: 11614},
        idle: {count: 30002},
        others: {count: 6774},
      },
    },
    {
      id: 'satellite',
      name: 'Satellite Lab',
      subtitle: 'Dedicated pools, funded and operated by partner teams',
      dotClass: 'dot-satellite',
      hosts: {count: 2894},
      devices: {count: 78632},
      utilization: {
        busy: {count: 14940},
        idle: {count: 45607},
        others: {count: 18085},
      },
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, BreakdownTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(BreakdownTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('rows', mockRows);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should not render table when defaultExpanded is false and not clicked', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.breakdown-table')).toBeNull();
    expect(compiled.textContent).toContain('View Breakdown');
  });

  it('should render the expand more chevron icon after the text label', () => {
    const button = fixture.nativeElement.querySelector(
      '.m3-button-toggle',
    ) as HTMLElement;
    const chevron = button.querySelector('.chevron-icon') as HTMLElement;
    expect(chevron).toBeTruthy();
    expect(chevron.hasAttribute('iconpositionend')).toBeTrue();
    expect(chevron.textContent?.trim()).toBe('expand_more');
  });

  it('should toggle expanded state when button is clicked', () => {
    expect(component.expanded()).toBeFalse();

    const button = fixture.nativeElement.querySelector(
      '.m3-button-toggle',
    ) as HTMLElement;
    button.click();
    fixture.detectChanges();

    expect(component.expanded()).toBeTrue();
    expect(
      fixture.nativeElement.querySelector('.breakdown-table'),
    ).toBeTruthy();

    button.click();
    fixture.detectChanges();

    expect(component.expanded()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.breakdown-table'),
    ).toBeNull();
  });

  it('should render table expanded when expanded is set to true', async () => {
    fixture = TestBed.createComponent(BreakdownTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('rows', mockRows);
    fixture.componentRef.setInput('expanded', true);
    fixture.componentRef.setInput('toggleLabel', 'Breakdown by Lab Type');
    fixture.componentRef.setInput('nameColumnHeader', 'Lab Type');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.breakdown-table')).toBeTruthy();
    expect(compiled.textContent).toContain('Hide Breakdown by Lab Type');
    expect(compiled.textContent).toContain('Lab Type');
    expect(compiled.textContent).toContain('Core Lab');
    expect(compiled.textContent).toContain('Shared pool, operated by OmniLab team');
    expect(compiled.textContent).toContain('Satellite Lab');
    expect(compiled.textContent).toContain('1,420');
    expect(compiled.textContent).toContain('48,390');

    const miniBars = compiled.querySelectorAll('app-utilization-bar');
    expect(miniBars.length).toBe(2);

    const coreDot = compiled.querySelector('.legend-dot.dot-core');
    expect(coreDot).toBeTruthy();
  });

  it('should render drilldown links for hosts and devices', async () => {
    fixture.componentRef.setInput('expanded', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const links = compiled.querySelectorAll(
      'tr.drill-row td.drill-cell a.clickable-number',
    );
    expect(links.length).toBe(4); // 2 rows * 2 links

    const hostLink = links[0] as HTMLAnchorElement;
    const hostHref = decodeURIComponent(hostLink.getAttribute('href') || '');
    expect(hostHref).toContain('/hosts');
    expect(hostHref).toContain('host_field::lab_type~Core Lab');

    const devLink = links[1] as HTMLAnchorElement;
    const devHref = decodeURIComponent(devLink.getAttribute('href') || '');
    expect(devHref).toContain('/devices');
    expect(devHref).toContain('host_field::lab_type~Core Lab');
  });

  it('should not render anything when rows is empty', () => {
    fixture.componentRef.setInput('rows', []);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.m3-button-toggle')).toBeNull();
    expect(compiled.querySelector('.breakdown-table')).toBeNull();
  });

  it('should handle undefined rows gracefully via input transform', () => {
    fixture.componentRef.setInput('rows', undefined);
    fixture.detectChanges();

    expect(component.rows()).toEqual([]);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.m3-button-toggle')).toBeNull();
    expect(compiled.querySelector('.breakdown-table')).toBeNull();
  });
});
