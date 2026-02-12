import { create } from "zustand";

interface AlertsStoreState {
  alerts: Record<string, any>;
  pushAlert: (widget: string, alert: any) => void;
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
