/**
 * UBA Analytics Library
 * Universal User Behavior Analytics for Web Applications
 * 
 * Usage:
 * import { UBAAnalytics } from './uba-analytics';
 * 
 * // Initialize
 * const uba = new UBAAnalytics({
 *   enableInspectBlocking: true,
 *   enableLocationTracking: true,
 *   apiEndpoints: ['/api/auth/login', '/api/auth/register'] // Optional: specific endpoints
 * });
 * 
 * // Start tracking
 * uba.startTracking();
 * 
 * // Manual data retrieval (optional)
 * const data = uba.getBehaviorData(payload);
 */

export interface UBAConfig {
  enableInspectBlocking?: boolean;
  enableLocationTracking?: boolean;
  enableIPTracking?: boolean;
  apiEndpoints?: string[];
  onDataCapture?: (data: any) => void;
  locationTimeout?: number;
}

export interface BehaviorMetadata {
  keysPressed: { key: string, time: number }[];
  mouseClicks: { x: number, y: number, time: number, element: string }[];
  mouseHovers: { x: number, y: number, time: number, element: string }[];
  scrollEvents: { scrollY: number, time: number }[];
  timeSpent: number;
  currentPage: string;
  timestamp: string;
  userAgent: string;
  screenResolution: string;
  ipAddress: string;
  location: {
    latitude: number | null;
    longitude: number | null;
    accuracy: number | null;
    locationName: string | null;
    error: string | null;
  };
}

export class UBAAnalytics {
  private config: UBAConfig;
  private keyLogs: { key: string, time: number }[] = [];
  private mouseClicks: { x: number, y: number, time: number, element: string }[] = [];
  private mouseHovers: { x: number, y: number, time: number, element: string }[] = [];
  private scrollEvents: { scrollY: number, time: number }[] = [];
  private pageLoadTime = Date.now();
  private currentPage = '';
  private ipAddress = '';
  private location: {
    latitude: number | null;
    longitude: number | null;
    accuracy: number | null;
    locationName: string | null;
    error: string | null;
  } = { latitude: null, longitude: null, accuracy: null, locationName: null, error: null };
  
  private isTracking = false;
  private originalFetch: typeof fetch;
  private originalXHROpen: typeof XMLHttpRequest.prototype.open;

  constructor(config: UBAConfig = {}) {
    this.config = {
      enableInspectBlocking: true,
      enableLocationTracking: true,
      enableIPTracking: true,
      locationTimeout: 10000,
      ...config
    };

    if (typeof window !== 'undefined') {
      this.currentPage = window.location.pathname;
      this.originalFetch = window.fetch;
      this.originalXHROpen = XMLHttpRequest.prototype.open;
    }
  }

  public startTracking(): void {
    if (typeof window === 'undefined' || this.isTracking) return;
    
    this.isTracking = true;
    this.setupEventListeners();
    this.setupNetworkInterception();
    
    if (this.config.enableInspectBlocking) {
      this.disableInspectElement();
    }
    
    if (this.config.enableIPTracking) {
      this.getIPAddress();
    }
    
    if (this.config.enableLocationTracking) {
      this.getLocation();
    }
  }

  public stopTracking(): void {
    this.isTracking = false;
    this.removeEventListeners();
    this.restoreNetworkMethods();
  }

  private setupEventListeners(): void {
    document.addEventListener('click', this.handleClick.bind(this));
    document.addEventListener('mouseover', this.handleMouseOver.bind(this));
    document.addEventListener('keydown', this.handleKeyDown.bind(this));
    document.addEventListener('scroll', this.handleScroll.bind(this));
  }

  private removeEventListeners(): void {
    document.removeEventListener('click', this.handleClick.bind(this));
    document.removeEventListener('mouseover', this.handleMouseOver.bind(this));
    document.removeEventListener('keydown', this.handleKeyDown.bind(this));
    document.removeEventListener('scroll', this.handleScroll.bind(this));
  }

  private handleClick(e: MouseEvent): void {
    const target = e.target as HTMLElement;
    this.mouseClicks.push({
      x: e.clientX,
      y: e.clientY,
      time: Date.now(),
      element: this.getElementSelector(target)
    });
  }

  private handleMouseOver(e: MouseEvent): void {
    const target = e.target as HTMLElement;
    this.mouseHovers.push({
      x: e.clientX,
      y: e.clientY,
      time: Date.now(),
      element: this.getElementSelector(target)
    });
  }

  private handleKeyDown(e: KeyboardEvent): void {
    this.keyLogs.push({
      key: e.key,
      time: Date.now()
    });
  }

  private handleScroll(): void {
    this.scrollEvents.push({
      scrollY: window.scrollY,
      time: Date.now()
    });
  }

  private getElementSelector(element: HTMLElement): string {
    return element.tagName + 
           (element.id ? `#${element.id}` : '') + 
           (element.className ? `.${element.className.split(' ').join('.')}` : '');
  }

  private setupNetworkInterception(): void {
    const self = this;
    
    // Intercept fetch
    window.fetch = async function(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
      const url = typeof input === 'string' ? input : input.toString();
      
      if (self.shouldInterceptRequest(url)) {
        const behaviorData = self.getBehaviorMetadata();
        const originalBody = init?.body;
        
        const modifiedInit = {
          ...init,
          body: JSON.stringify({
            payload: originalBody ? JSON.parse(originalBody as string) : {},
            metadata: behaviorData
          }),
          headers: {
            ...init?.headers,
            'Content-Type': 'application/json'
          }
        };
        
        if (self.config.onDataCapture) {
          self.config.onDataCapture({ payload: originalBody, metadata: behaviorData });
        }
        
        return self.originalFetch(input, modifiedInit);
      }
      
      return self.originalFetch(input, init);
    };

    // Intercept XMLHttpRequest
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, ...args: any[]) {
      const urlString = url.toString();
      
      if (self.shouldInterceptRequest(urlString)) {
        const originalSend = this.send;
        this.send = function(body?: Document | XMLHttpRequestBodyInit | null) {
          const behaviorData = self.getBehaviorMetadata();
          const modifiedBody = JSON.stringify({
            payload: body ? JSON.parse(body as string) : {},
            metadata: behaviorData
          });
          
          if (self.config.onDataCapture) {
            self.config.onDataCapture({ payload: body, metadata: behaviorData });
          }
          
          this.setRequestHeader('Content-Type', 'application/json');
          return originalSend.call(this, modifiedBody);
        };
      }
      
      return self.originalXHROpen.call(this, method, url, ...args);
    };
  }

  private shouldInterceptRequest(url: string): boolean {
    if (this.config.apiEndpoints && this.config.apiEndpoints.length > 0) {
      return this.config.apiEndpoints.some(endpoint => url.includes(endpoint));
    }
    return url.includes('/api/');
  }

  private restoreNetworkMethods(): void {
    if (typeof window !== 'undefined') {
      window.fetch = this.originalFetch;
      XMLHttpRequest.prototype.open = this.originalXHROpen;
    }
  }

  private async getIPAddress(): Promise<void> {
    try {
      const response = await fetch('https://api.ipify.org?format=json');
      const data = await response.json();
      this.ipAddress = data.ip;
    } catch {
      this.ipAddress = 'unavailable';
    }
  }

  private getLocation(): void {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.location = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            locationName: null,
            error: null
          };
          this.getLocationName(position.coords.latitude, position.coords.longitude);
        },
        (error) => {
          this.location.error = error.message;
          this.getIPLocation();
        },
        { enableHighAccuracy: true, timeout: this.config.locationTimeout, maximumAge: 600000 }
      );
    } else {
      this.location.error = 'Geolocation not supported';
      this.getIPLocation();
    }
  }

  private async getIPLocation(): Promise<void> {
    try {
      const response = await fetch('http://ip-api.com/json/');
      const data = await response.json();
      if (data.status === 'success') {
        this.location = {
          latitude: data.lat,
          longitude: data.lon,
          accuracy: null,
          locationName: `${data.city}, ${data.regionName}, ${data.country}`,
          error: 'IP-based location (approximate)'
        };
      }
    } catch {
      this.location.error = 'Location unavailable';
    }
  }

  private async getLocationName(lat: number, lon: number): Promise<void> {
    try {
      const response = await fetch(`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`);
      const data = await response.json();
      this.location.locationName = `${data.city || data.locality}, ${data.principalSubdivision}, ${data.countryName}`;
    } catch {
      this.location.locationName = 'Location name unavailable';
    }
  }

  private disableInspectElement(): void {
    document.addEventListener('contextmenu', e => e.preventDefault());
    document.addEventListener('selectstart', e => e.preventDefault());
    document.addEventListener('dragstart', e => e.preventDefault());

    document.onkeydown = (e) => {
      if (e.key === "F12" || 
          (e.ctrlKey && e.shiftKey && (e.key === 'I' || e.key === 'C' || e.key === 'J')) ||
          (e.ctrlKey && e.key === 'U') ||
          (e.ctrlKey && e.key === 'S')) {
        e.preventDefault();
        return false;
      }
      return true;
    };
  }

  public getBehaviorData(inputPayload?: any): { payload: any; metadata: BehaviorMetadata } {
    return {
      payload: inputPayload,
      metadata: this.getBehaviorMetadata()
    };
  }

  private getBehaviorMetadata(): BehaviorMetadata {
    return {
      keysPressed: this.keyLogs,
      mouseClicks: this.mouseClicks,
      mouseHovers: this.mouseHovers,
      scrollEvents: this.scrollEvents,
      timeSpent: Date.now() - this.pageLoadTime,
      currentPage: this.currentPage,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      screenResolution: `${screen.width}x${screen.height}`,
      ipAddress: this.ipAddress,
      location: this.location
    };
  }

  public resetPageTracking(): void {
    this.pageLoadTime = Date.now();
    this.currentPage = window.location.pathname;
  }

  public clearBehaviorData(): void {
    this.keyLogs = [];
    this.mouseClicks = [];
    this.mouseHovers = [];
    this.scrollEvents = [];
  }

  public getTrackingStatus(): boolean {
    return this.isTracking;
  }
}