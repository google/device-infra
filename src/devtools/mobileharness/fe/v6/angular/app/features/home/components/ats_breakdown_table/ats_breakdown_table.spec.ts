import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {
  Fleet,
  PartnerAtsLab,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';
import {AtsBreakdownTable} from './ats_breakdown_table';

describe('AtsBreakdownTable Component', () => {
  let component: AtsBreakdownTable;
  let fixture: ComponentFixture<AtsBreakdownTable>;

  const mockLabs: readonly PartnerAtsLab[] = [
    {
      controllerId: 'xiaomi-ctrl',
      displayName: 'Xiaomi',
      hosts: {
        count: 5,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_HOST,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'host::ats_controller',
              simple: {values: [{value: 'xiaomi-ctrl'}]},
            },
          ],
        },
      },
      devices: {
        count: 50,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'host::ats_controller',
              simple: {values: [{value: 'xiaomi-ctrl'}]},
            },
          ],
        },
      },
      utilization: {
        busy: {count: 30},
        idle: {count: 15},
        others: {count: 5},
      },
    },
    {
      controllerId: 'samsung-ctrl',
      displayName: 'Samsung',
      hosts: {count: 8},
      devices: {count: 80},
      utilization: {
        busy: {count: 40},
        idle: {count: 30},
        others: {count: 10},
      },
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, AtsBreakdownTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AtsBreakdownTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('labs', mockLabs);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should not render table when expanded is false', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.ats-breakdown-table')).toBeNull();
    expect(compiled.textContent).toContain(
      'View Breakdown by Lab (Controller)',
    );
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
      fixture.nativeElement.querySelector('.ats-breakdown-table'),
    ).toBeTruthy();

    button.click();
    fixture.detectChanges();

    expect(component.expanded()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.ats-breakdown-table'),
    ).toBeNull();
  });

  it('should render table when expanded is true', () => {
    fixture.componentRef.setInput('expanded', true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.ats-breakdown-table')).toBeTruthy();
    expect(compiled.textContent).toContain(
      'Hide Breakdown by Lab (Controller)',
    );
    expect(compiled.textContent).toContain('Xiaomi');
    expect(compiled.textContent).toContain('Samsung');
    expect(compiled.textContent).toContain('50');
    expect(compiled.textContent).toContain('80');

    const miniBars = compiled.querySelectorAll('app-utilization-bar');
    expect(miniBars.length).toBe(2);
  });

  it('should render native links with drilldown route in host and device cells', () => {
    fixture.componentRef.setInput('expanded', true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const links = compiled.querySelectorAll(
      'tr.drill-row td.drill-cell a.clickable-number',
    );
    expect(links.length).toBe(4); // 2 rows * 2 links (hosts, devices)
    const firstHostLink = links[0] as HTMLAnchorElement;
    expect(firstHostLink.getAttribute('href')).toContain('/hosts');
    expect(firstHostLink.getAttribute('href')).toContain(
      'host::ats_controller~xiaomi-ctrl',
    );
    expect(firstHostLink.getAttribute('href')).toContain('fleet=ats');

    const firstDeviceLink = links[1] as HTMLAnchorElement;
    expect(firstDeviceLink.getAttribute('href')).toContain('/devices');
    expect(firstDeviceLink.getAttribute('href')).toContain(
      'host::ats_controller~xiaomi-ctrl',
    );
    expect(firstDeviceLink.getAttribute('href')).toContain('fleet=ats');
  });

  it('should handle undefined labs gracefully via input transform', () => {
    fixture.componentRef.setInput('labs', undefined);
    fixture.componentRef.setInput('expanded', true);
    fixture.detectChanges();

    expect(component.labs()).toEqual([]);
    const rows = fixture.nativeElement.querySelectorAll('tr.drill-row');
    expect(rows.length).toBe(0);
  });
});
