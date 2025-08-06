# UBA Analytics Library

Universal User Behavior Analytics for Web Applications

## 🚀 Quick Start

### Vanilla JavaScript
```javascript
import { UBAAnalytics } from './uba-analytics';

const uba = new UBAAnalytics({
  enableInspectBlocking: true,
  enableLocationTracking: true,
  apiEndpoints: ['/api/auth/login', '/api/auth/register']
});

uba.startTracking();
```

### Angular
```typescript
// app.config.ts
import { UBAAngularService } from './angular-integration';

// Component
constructor(private uba: UBAAngularService) {}

ngOnInit() {
  this.uba.configure({
    enableInspectBlocking: true,
    enableLocationTracking: true
  });
}
```

### React
```jsx
import { UBAProvider, useUBA } from './react-integration';

// App.jsx
function App() {
  return (
    <UBAProvider config={{ enableLocationTracking: true }}>
      <MyComponent />
    </UBAProvider>
  );
}

// Component
function MyComponent() {
  const { getBehaviorData, clearData } = useUBA();
  
  const handleSubmit = (formData) => {
    const data = getBehaviorData(formData);
    console.log(data); // { payload: formData, metadata: {...} }
  };
}
```

### Vue.js
```javascript
// main.js
import { UBAPlugin } from './vue-integration';

app.use(UBAPlugin, {
  enableLocationTracking: true,
  enableInspectBlocking: true
});

// Component
import { useUBAInstance } from './vue-integration';

export default {
  setup() {
    const { getBehaviorData, clearData } = useUBAInstance();
    
    const handleSubmit = (formData) => {
      const data = getBehaviorData(formData);
      console.log(data);
    };
    
    return { handleSubmit };
  }
}
```

## 📊 Features

### Automatic Tracking
- ✅ Mouse clicks with element identification
- ✅ Mouse hovers and movements
- ✅ Keyboard inputs with timestamps
- ✅ Scroll behavior
- ✅ Time spent on page
- ✅ IP address detection
- ✅ GPS/IP-based location tracking
- ✅ Device and browser information

### Network Interception
- ✅ Automatic API request interception
- ✅ Structured payload + metadata format
- ✅ Support for fetch() and XMLHttpRequest
- ✅ Configurable endpoint filtering

### Security Features
- ✅ Developer tools blocking
- ✅ Right-click prevention
- ✅ Text selection blocking
- ✅ Keyboard shortcut blocking

## ⚙️ Configuration

```javascript
const config = {
  enableInspectBlocking: true,        // Block developer tools
  enableLocationTracking: true,       // Request user location
  enableIPTracking: true,             // Get IP address
  apiEndpoints: ['/api/*'],           // Specific endpoints to intercept
  locationTimeout: 10000,             // Location request timeout
  onDataCapture: (data) => {          // Custom data handler
    console.log('Captured:', data);
  }
};
```

## 📦 Data Structure

Every API call automatically includes:

```json
{
  "payload": {
    // Your original request data
    "email": "user@example.com",
    "password": "********"
  },
  "metadata": {
    "keysPressed": [
      { "key": "u", "time": 1703123456789 },
      { "key": "s", "time": 1703123456790 }
    ],
    "mouseClicks": [
      { "x": 450, "y": 200, "time": 1703123456791, "element": "BUTTON#submit.btn" }
    ],
    "mouseHovers": [...],
    "scrollEvents": [...],
    "timeSpent": 45000,
    "currentPage": "/login",
    "timestamp": "2024-01-01T12:00:00.000Z",
    "userAgent": "Mozilla/5.0...",
    "screenResolution": "1920x1080",
    "ipAddress": "192.168.1.100",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "accuracy": 10,
      "locationName": "New York, NY, United States",
      "error": null
    }
  }
}
```

## 🔧 API Methods

```javascript
// Start/stop tracking
uba.startTracking();
uba.stopTracking();

// Manual data retrieval
const data = uba.getBehaviorData(payload);

// Data management
uba.clearBehaviorData();
uba.resetPageTracking();

// Status check
const isActive = uba.getTrackingStatus();
```

## 🛡️ Privacy & Security

- Requests user permission for location access
- Graceful fallbacks when permissions denied
- No sensitive data stored locally
- Configurable feature toggles
- Transparent data collection

## 📱 Browser Support

- ✅ Chrome 60+
- ✅ Firefox 55+
- ✅ Safari 12+
- ✅ Edge 79+

## 🚀 Installation

```bash
npm install uba-analytics-library
# or
yarn add uba-analytics-library
```

## 📄 License

MIT License - Use freely in commercial and open-source projects.