import { create } from "zustand";

export interface Alert {
  title: string;
  subtitle?: string;
  image?: string;
}

interface AlertsStoreState {
  alerts: Record<string, Alert>;
  pushAlert: (widget: string, alert: Alert) => void;
  clearAlert: (widget: string) => void;
}

const useAlertsStore = create<AlertsStoreState>(set => ({
  alerts: {},
  pushAlert: (widget, alert) => {
    set(state => ({
      alerts: {
        ...state.alerts,
        [widget]: alert,
      },
    }));
  },
  clearAlert: widget => {
    set(state => {
      const newAlerts = { ...state.alerts };
      delete newAlerts[widget];
      return { alerts: newAlerts };
    });
  },
}));

export default useAlertsStore;
