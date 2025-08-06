/**
 * React Integration for UBA Analytics
 * Provides React hooks and context providers
 */

import React, { createContext, useContext, useEffect, useRef } from 'react';
import { UBAAnalytics, UBAConfig } from './uba-analytics';

interface UBAContextType {
  uba: UBAAnalytics | null;
  getBehaviorData: (payload?: any) => any;
  clearData: () => void;
  resetPageTracking: () => void;
}

const UBAContext = createContext<UBAContextType | null>(null);

export const UBAProvider: React.FC<{ 
  children: React.ReactNode; 
  config?: UBAConfig 
}> = ({ children, config = {} }) => {
  const ubaRef = useRef<UBAAnalytics | null>(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      ubaRef.current = new UBAAnalytics(config);
      ubaRef.current.startTracking();
    }

    return () => {
      ubaRef.current?.stopTracking();
    };
  }, []);

  const contextValue: UBAContextType = {
    uba: ubaRef.current,
    getBehaviorData: (payload?: any) => ubaRef.current?.getBehaviorData(payload),
    clearData: () => ubaRef.current?.clearBehaviorData(),
    resetPageTracking: () => ubaRef.current?.resetPageTracking()
  };

  return (
    <UBAContext.Provider value={contextValue}>
      {children}
    </UBAContext.Provider>
  );
};

export const useUBA = (): UBAContextType => {
  const context = useContext(UBAContext);
  if (!context) {
    throw new Error('useUBA must be used within a UBAProvider');
  }
  return context;
};

// React Hook for manual tracking
export const useUBATracking = (config?: UBAConfig) => {
  const ubaRef = useRef<UBAAnalytics | null>(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      ubaRef.current = new UBAAnalytics(config);
      ubaRef.current.startTracking();
    }

    return () => {
      ubaRef.current?.stopTracking();
    };
  }, []);

  return {
    getBehaviorData: (payload?: any) => ubaRef.current?.getBehaviorData(payload),
    clearData: () => ubaRef.current?.clearBehaviorData(),
    resetPageTracking: () => ubaRef.current?.resetPageTracking(),
    isTracking: () => ubaRef.current?.getTrackingStatus() || false
  };
};