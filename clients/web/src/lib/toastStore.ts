import { create } from 'zustand';

let timer: ReturnType<typeof setTimeout> | null = null;

interface ToastState {
  message: string | null;
  show: (message: string, duration?: number) => void;
  dismiss: () => void;
}

export const useToastStore = create<ToastState>((set) => ({
  message: null,
  show: (message, duration = 3000) => {
    if (timer) clearTimeout(timer);
    set({ message });
    timer = setTimeout(() => set({ message: null }), duration);
  },
  dismiss: () => {
    if (timer) clearTimeout(timer);
    set({ message: null });
  },
}));
