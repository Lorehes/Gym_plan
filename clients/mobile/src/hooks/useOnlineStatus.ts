import { useEffect, useSyncExternalStore } from 'react';
import NetInfo, { type NetInfoState } from '@react-native-community/netinfo';
import { onlineManager } from '@tanstack/react-query';

// 모듈 스코프 캐시 — 여러 구독자가 같은 NetInfo 리스너를 공유.
let currentState: { isConnected: boolean; isInternetReachable: boolean | null } = {
  isConnected: true,
  isInternetReachable: null,
};
const listeners = new Set<() => void>();

let netinfoUnsubscribe: (() => void) | null = null;

function applyState(state: NetInfoState) {
  currentState = {
    isConnected: state.isConnected ?? false,
    isInternetReachable: state.isInternetReachable,
  };
  listeners.forEach((l) => l());
}

function ensureSubscribed() {
  if (netinfoUnsubscribe) return;
  netinfoUnsubscribe = NetInfo.addEventListener(applyState);
  void NetInfo.fetch().then(applyState);
}

function subscribe(callback: () => void) {
  ensureSubscribed();
  listeners.add(callback);
  return () => {
    listeners.delete(callback);
    if (listeners.size === 0) {
      netinfoUnsubscribe?.();
      netinfoUnsubscribe = null;
    }
  };
}

function getSnapshot() {
  return currentState;
}

export interface OnlineStatus {
  isOnline: boolean;
  isConnected: boolean;
  isInternetReachable: boolean | null;
}

// 오프라인 감지: WiFi 연결 + 인터넷 도달 가능 둘 다 확인.
// `isInternetReachable === false`만 오프라인 — null(미확정)은 온라인 취급.
export function useOnlineStatus(): OnlineStatus {
  const state = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
  const isOnline = state.isConnected && state.isInternetReachable !== false;
  return {
    isOnline,
    isConnected: state.isConnected,
    isInternetReachable: state.isInternetReachable,
  };
}

// React Query에 NetInfo 연결 — 온라인 복귀 시 자동 refetch.
export function useReactQueryOnlineSync() {
  useEffect(() => {
    return onlineManager.subscribe(() => {});
  }, []);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      onlineManager.setOnline(
        (state.isConnected ?? false) && state.isInternetReachable !== false,
      );
    });
    return unsubscribe;
  }, []);
}
