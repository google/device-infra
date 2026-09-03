import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {
  DeviceUtilization,
  Fleet,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';
import {UtilizationBar} from './utilization_bar';

describe('UtilizationBar Component', () => {
  let component: UtilizationBar;
  let fixture: ComponentFixture<UtilizationBar>;

  const mockUtilization: DeviceUtilization = {
    busy: {
      count: 50,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [{key: 'field::status', simple: {values: [{value: 'BUSY'}]}}],
      },
    },
    idle: {
      count: 30,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [{key: 'field::status', simple: {values: [{value: 'IDLE'}]}}],
      },
    },
    others: {
      count: 20,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [{key: 'field::status', simple: {values: [{value: 'INIT'}]}}],
      },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, UtilizationBar],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(UtilizationBar);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('utilization', mockUtilization);
    fixture.componentRef.setInput('totalDevices', 100);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render full mode by default with correct segments and legends', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.mini-util-container')).toBeNull();
    const text = compiled.textContent || '';
    expect(text).toContain('In Service (BUSY)');
    expect(text).toContain('50 (50%)');
    expect(text).toContain('In Service (IDLE)');
    expect(text).toContain('30 (30%)');
    expect(text).toContain('Others');
    expect(text).toContain('20 (20%)');

    const busySegment = compiled.querySelector(
      '.progress-segment.color-busy',
    ) as HTMLElement;
    expect(busySegment).toBeTruthy();
    expect(busySegment.style.width).toBe('50%');

    const idleSegment = compiled.querySelector(
      '.progress-segment.color-idle',
    ) as HTMLElement;
    expect(idleSegment).toBeTruthy();
    expect(idleSegment.style.width).toBe('30%');

    const othersSegment = compiled.querySelector(
      '.progress-segment.color-others',
    ) as HTMLElement;
    expect(othersSegment).toBeTruthy();
    expect(othersSegment.style.width).toBe('20%');
  });

  it('should handle internal hover coordination', () => {
    expect(component.hoveredSegment()).toBeNull();

    component.setHover('busy');
    fixture.detectChanges();
    expect(component.hoveredSegment()).toBe('busy');

    const compiled = fixture.nativeElement as HTMLElement;
    const busySegment = compiled.querySelector('.progress-segment.color-busy');
    expect(busySegment?.classList.contains('hl')).toBeTrue();

    component.setHover(null);
    fixture.detectChanges();
    expect(component.hoveredSegment()).toBeNull();
    expect(busySegment?.classList.contains('hl')).toBeFalse();
  });

  it('should render native links on segments with drilldown route', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const busySegment = compiled.querySelector(
      '.progress-segment.color-busy',
    ) as HTMLAnchorElement;
    expect(busySegment.getAttribute('href')).toContain('/devices');
    expect(busySegment.getAttribute('href')).toContain('field::status~BUSY');
  });

  it('should render native links on legends with drilldown route', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const idleLegend = compiled.querySelector(
      '.legend-floating',
    ) as HTMLAnchorElement;
    expect(idleLegend.getAttribute('href')).toContain('/devices');
    expect(idleLegend.getAttribute('href')).toContain('field::status~IDLE');
  });

  it('should render mini mode when mode is mini', () => {
    fixture.componentRef.setInput('mode', 'mini');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.mini-util-container')).toBeTruthy();
    expect(compiled.querySelector('.progress-container.mini-bar')).toBeTruthy();

    const text = compiled.textContent || '';
    expect(text).toContain('50 (50%)');
    expect(text).toContain('30 (30%)');
    expect(text).toContain('20 (20%)');
  });

  it('should auto-derive totalDevices from utilization counts when totalDevices is omitted', () => {
    fixture.componentRef.setInput('totalDevices', undefined);
    fixture.detectChanges();
    expect(component.metrics().busyPct).toBe(50);
    expect(component.metrics().idlePct).toBe(30);
    expect(component.metrics().othersPct).toBe(20);
  });
});
