/**
 * Angular Integration for UBA Analytics
 * Provides Angular-specific services and interceptors
 */

import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { UBAAnalytics, UBAConfig } from './uba-analytics';

@Injectable({
  providedIn: 'root'
})
export class UBAAngularService {
  private uba: UBAAnalytics;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    if (isPlatformBrowser(this.platformId)) {
      this.uba = new UBAAnalytics();
      this.uba.startTracking();
    }
  }

  public configure(config: UBAConfig): void {
    if (isPlatformBrowser(this.platformId)) {
      this.uba.stopTracking();
      this.uba = new UBAAnalytics(config);
      this.uba.startTracking();
    }
  }

  public getBehaviorData(payload?: any) {
    return this.uba?.getBehaviorData(payload);
  }

  public clearData(): void {
    this.uba?.clearBehaviorData();
  }

  public resetPageTracking(): void {
    this.uba?.resetPageTracking();
  }
}

// Angular HTTP Interceptor
export const ubaInterceptor: HttpInterceptorFn = (req, next) => {
  // This would be injected automatically if UBAAngularService is used
  return next(req);
};