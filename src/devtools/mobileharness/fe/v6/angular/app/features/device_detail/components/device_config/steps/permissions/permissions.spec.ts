import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Permissions} from './permissions';

describe('Permissions Component', () => {
  let fixture: ComponentFixture<Permissions>;
  let component: Permissions;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Permissions,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Permissions);
    component = fixture.componentInstance;
    fixture.componentRef.setInput(
      'permissions',
      SCENARIO_IN_SERVICE_IDLE.config!.permissions || {},
    );
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('Editability', () => {
    it('should enable items when editable is true', () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: true},
      });
      fixture.componentRef.setInput('permissions', {
        owners: ['owner1'],
        executors: ['exec1'],
      });
      fixture.componentRef.setInput('type', 'device');
      fixture.detectChanges();

      const permissions = component.PERMISSIONS();
      expect(permissions.length).toBeGreaterThan(0);
      for (const item of permissions) {
        expect(item.editable).toBeTrue();
      }
    });

    it('should disable items when editable is false', () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: false},
      });
      fixture.componentRef.setInput('permissions', {
        owners: ['owner1'],
        executors: ['exec1'],
      });
      fixture.componentRef.setInput('type', 'device');
      fixture.detectChanges();

      const permissions = component.PERMISSIONS();
      expect(permissions.length).toBeGreaterThan(0);
      for (const item of permissions) {
        expect(item.editable).toBeFalse();
      }
    });

    it('should disable items when editable is undefined (omitted)', () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {}, // editable is undefined
      });
      fixture.componentRef.setInput('permissions', {
        owners: ['owner1'],
        executors: ['exec1'],
      });
      fixture.componentRef.setInput('type', 'device');
      fixture.detectChanges();

      const permissions = component.PERMISSIONS();
      expect(permissions.length).toBeGreaterThan(0);
      for (const item of permissions) {
        expect(item.editable).toBeFalse();
      }
    });
  });

  describe('Type', () => {
    it('should return host permissions when type is host', () => {
      fixture.componentRef.setInput('type', 'host');
      fixture.componentRef.setInput('permissions', {
        owners: ['owner1'],
        executors: ['exec1'],
      });
      fixture.detectChanges();

      const permissions = component.PERMISSIONS();
      expect(permissions.length).toBe(2);
      expect(permissions[0].type).toBe('owners');
      expect(permissions[0].editable).toBeFalse();
      expect(permissions[1].type).toBe('executors');
    });

    it('should return empty list when type is invalid', () => {
      fixture.componentRef.setInput('type', 'invalid' as unknown as 'device');
      fixture.detectChanges();

      const permissions = component.PERMISSIONS();
      expect(permissions.length).toBe(0);
    });
  });
});
