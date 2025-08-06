/**
 * Vue.js Integration for UBA Analytics
 * Provides Vue composables and plugins
 */

import { ref, onMounted, onUnmounted, inject, provide } from 'vue';
import type { App, InjectionKey } from 'vue';
import { UBAAnalytics, UBAConfig } from './uba-analytics';

const UBAKey: InjectionKey<UBAAnalytics> = Symbol('UBA');

// Vue Plugin
export const UBAPlugin = {
  install(app: App, config: UBAConfig = {}) {
    if (typeof window !== 'undefined') {
      const uba = new UBAAnalytics(config);
      uba.startTracking();
      
      app.provide(UBAKey, uba);
      app.config.globalProperties.$uba = uba;
    }
  }
};

// Vue Composable
export function useUBA(config?: UBAConfig) {
  const uba = ref<UBAAnalytics | null>(null);

  onMounted(() => {
    if (typeof window !== 'undefined') {
      uba.value = new UBAAnalytics(config);
      uba.value.startTracking();
    }
  });

  onUnmounted(() => {
    uba.value?.stopTracking();
  });

  const getBehaviorData = (payload?: any) => {
    return uba.value?.getBehaviorData(payload);
  };

  const clearData = () => {
    uba.value?.clearBehaviorData();
  };

  const resetPageTracking = () => {
    uba.value?.resetPageTracking();
  };

  return {
    uba,
    getBehaviorData,
    clearData,
    resetPageTracking
  };
}

// Use injected UBA instance
export function useUBAInstance() {
  const uba = inject(UBAKey);
  if (!uba) {
    throw new Error('UBA not provided. Make sure to install UBAPlugin.');
  }

  return {
    getBehaviorData: (payload?: any) => uba.getBehaviorData(payload),
    clearData: () => uba.clearBehaviorData(),
    resetPageTracking: () => uba.resetPageTracking()
  };
}