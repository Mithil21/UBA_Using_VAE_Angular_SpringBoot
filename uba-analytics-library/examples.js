// Example usage in different frameworks

// ============= VANILLA JAVASCRIPT =============
import { UBAAnalytics } from 'uba-analytics-library';

const uba = new UBAAnalytics({
  enableInspectBlocking: true,
  enableLocationTracking: true,
  apiEndpoints: ['/api/auth/login', '/api/auth/register', '/api/submit'],
  onDataCapture: (data) => {
    console.log('Behavior data captured:', data);
  }
});

uba.startTracking();

// Manual usage
document.getElementById('submit-btn').addEventListener('click', () => {
  const formData = { email: 'user@example.com', password: 'pass123' };
  const behaviorData = uba.getBehaviorData(formData);
  
  // Send to your API
  fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(behaviorData) // Already structured as { payload, metadata }
  });
});

// ============= ANGULAR =============
// app.config.ts
import { UBAAngularService } from 'uba-analytics-library/angular';

export const appConfig: ApplicationConfig = {
  providers: [
    UBAAngularService,
    // ... other providers
  ]
};

// component.ts
import { Component } from '@angular/core';
import { UBAAngularService } from 'uba-analytics-library/angular';

@Component({
  selector: 'app-login',
  template: `
    <form (ngSubmit)="onSubmit()">
      <input [(ngModel)]="email" placeholder="Email">
      <input [(ngModel)]="password" type="password" placeholder="Password">
      <button type="submit">Login</button>
    </form>
  `
})
export class LoginComponent {
  email = '';
  password = '';

  constructor(private uba: UBAAngularService) {
    this.uba.configure({
      enableLocationTracking: true,
      enableInspectBlocking: true
    });
  }

  onSubmit() {
    const payload = { email: this.email, password: this.password };
    
    // Option 1: Manual data retrieval
    const data = this.uba.getBehaviorData(payload);
    console.log(data);
    
    // Option 2: Let interceptor handle automatically
    this.http.post('/api/auth/login', payload).subscribe(response => {
      console.log('Login successful');
      this.uba.clearData();
    });
  }
}

// ============= REACT =============
import React, { useState } from 'react';
import { UBAProvider, useUBA } from 'uba-analytics-library/react';

// App.jsx
function App() {
  return (
    <UBAProvider config={{ 
      enableLocationTracking: true,
      enableInspectBlocking: true,
      apiEndpoints: ['/api/*']
    }}>
      <LoginForm />
    </UBAProvider>
  );
}

// LoginForm.jsx
function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { getBehaviorData, clearData } = useUBA();

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    const payload = { email, password };
    const data = getBehaviorData(payload);
    
    console.log('Sending data:', data);
    
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      });
      
      if (response.ok) {
        clearData(); // Clear tracking data after successful login
      }
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input 
        value={email} 
        onChange={(e) => setEmail(e.target.value)}
        placeholder="Email" 
      />
      <input 
        type="password"
        value={password} 
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Password" 
      />
      <button type="submit">Login</button>
    </form>
  );
}

// ============= VUE.JS =============
// main.js
import { createApp } from 'vue';
import { UBAPlugin } from 'uba-analytics-library/vue';
import App from './App.vue';

const app = createApp(App);

app.use(UBAPlugin, {
  enableLocationTracking: true,
  enableInspectBlocking: true,
  apiEndpoints: ['/api/auth/login', '/api/auth/register']
});

app.mount('#app');

// LoginForm.vue
<template>
  <form @submit.prevent="handleSubmit">
    <input v-model="email" placeholder="Email" />
    <input v-model="password" type="password" placeholder="Password" />
    <button type="submit">Login</button>
  </form>
</template>

<script setup>
import { ref } from 'vue';
import { useUBAInstance } from 'uba-analytics-library/vue';

const email = ref('');
const password = ref('');
const { getBehaviorData, clearData } = useUBAInstance();

const handleSubmit = async () => {
  const payload = { 
    email: email.value, 
    password: password.value 
  };
  
  const data = getBehaviorData(payload);
  console.log('Behavior data:', data);
  
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    
    if (response.ok) {
      clearData();
    }
  } catch (error) {
    console.error('Login failed:', error);
  }
};
</script>

// ============= NEXT.JS =============
// pages/_app.js
import { UBAProvider } from 'uba-analytics-library/react';

export default function App({ Component, pageProps }) {
  return (
    <UBAProvider config={{ 
      enableLocationTracking: true,
      apiEndpoints: ['/api/*']
    }}>
      <Component {...pageProps} />
    </UBAProvider>
  );
}

// ============= NUXT.JS =============
// plugins/uba.client.js
import { UBAPlugin } from 'uba-analytics-library/vue';

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.use(UBAPlugin, {
    enableLocationTracking: true,
    enableInspectBlocking: true
  });
});

// ============= EXPRESS.JS BACKEND =============
// Handle the structured data on backend
app.post('/api/login', (req, res) => {
  const { payload, metadata } = req.body;
  
  console.log('User credentials:', payload);
  console.log('Behavior analytics:', metadata);
  
  // Analyze behavior for fraud detection
  const suspiciousActivity = analyzeBehavior(metadata);
  
  if (suspiciousActivity) {
    return res.status(403).json({ error: 'Suspicious activity detected' });
  }
  
  // Process normal login
  authenticateUser(payload.email, payload.password)
    .then(user => res.json({ success: true, user }))
    .catch(err => res.status(401).json({ error: 'Invalid credentials' }));
});

function analyzeBehavior(metadata) {
  // Example fraud detection logic
  const { keysPressed, mouseClicks, timeSpent } = metadata;
  
  // Check for bot-like behavior
  if (timeSpent < 2000) return true; // Too fast
  if (keysPressed.length === 0) return true; // No typing detected
  if (mouseClicks.length === 0) return true; // No mouse interaction
  
  return false;
}