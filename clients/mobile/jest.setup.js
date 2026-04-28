/* eslint-disable */
require('@react-native-async-storage/async-storage/jest/async-storage-mock');

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

jest.mock('@react-native-community/netinfo', () => {
  const listeners = new Set();
  let state = { isConnected: true, isInternetReachable: true };
  const mock = {
    addEventListener: jest.fn((cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    }),
    fetch: jest.fn(() => Promise.resolve(state)),
    __setState: (next) => {
      state = { ...state, ...next };
      listeners.forEach((cb) => cb(state));
    },
    __reset: () => {
      listeners.clear();
      state = { isConnected: true, isInternetReachable: true };
    },
  };
  return { __esModule: true, default: mock, ...mock };
});

jest.mock('react-native/Libraries/Alert/Alert', () => ({
  alert: jest.fn(),
}));
