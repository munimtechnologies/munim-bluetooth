import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Dimensions,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import * as MunimBluetooth from 'munim-bluetooth';
import type {
  BLEDevice,
  BluetoothCapabilities,
  GATTService,
  MultipeerPeer,
} from 'munim-bluetooth';

const TEST_SERVICE_UUID = '71f271d0-8f4c-4c4d-8a2d-6f3a9497b41d';
const TEST_CHARACTERISTIC_UUID = '4ad4a6d2-3f4a-477c-9832-5e0d8f7654d8';
const TEST_DESCRIPTOR_UUID = '00002901-0000-1000-8000-00805f9b34fb';
const TEST_LOCAL_NAME = `MunimBT-${Platform.OS}`;
const INITIAL_VALUE = '70696e67';
const UPDATED_VALUE = '706f6e67';
const MULTIPEER_SERVICE_TYPE = 'munim-mesh';
const MULTIPEER_MESSAGE_VALUE = '6d756e696d2d6d6573682d70696e67';
const IOS_TABLET_SHORT_SIDE = 700;
const SCREEN_SIZE = Dimensions.get('screen');
const IS_IOS_TABLET =
  Platform.OS === 'ios' &&
  Math.min(SCREEN_SIZE.width, SCREEN_SIZE.height) >= IOS_TABLET_SHORT_SIDE;
const SHOULD_AUTO_CONNECT_TO_PEER =
  Platform.OS === 'android' || (Platform.OS === 'ios' && !IS_IOS_TABLET);
const SHOULD_SCAN_FOR_PEERS = SHOULD_AUTO_CONNECT_TO_PEER;
const MAX_AUTO_PEERS = 4;
const DEVICE_ROLE =
  Platform.OS === 'ios'
    ? IS_IOS_TABLET
      ? 'ios-pad-peripheral'
      : 'ios-phone-central'
    : 'android-central';

type LogKind = 'info' | 'success' | 'warning' | 'error';

type LogEntry = {
  id: number;
  message: string;
  kind: LogKind;
};

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function hasTestService(device: BLEDevice): boolean {
  const services = [
    ...(device.serviceUUIDs ?? []),
    ...(device.advertisingData?.completeServiceUUIDs128 ?? []),
    ...(device.advertisingData?.incompleteServiceUUIDs128 ?? []),
  ];

  return services.some(
    (uuid) => uuid.toLowerCase() === TEST_SERVICE_UUID.toLowerCase()
  );
}

function hasTestPeerName(device: BLEDevice): boolean {
  const advertisedName = device.localName ?? device.name;
  return advertisedName === 'MunimBT-ios' || advertisedName === 'MunimBT-android';
}

function isTestPeer(device: BLEDevice): boolean {
  return hasTestService(device) || hasTestPeerName(device);
}

function buildTestService(): GATTService {
  return {
    uuid: TEST_SERVICE_UUID,
    characteristics: [
      {
        uuid: TEST_CHARACTERISTIC_UUID,
        properties: ['read', 'write', 'writeWithoutResponse', 'notify'],
        value: INITIAL_VALUE,
        descriptors: [
          {
            uuid: TEST_DESCRIPTOR_UUID,
            value: '4d756e696d20424c4520736d6f6b65',
            permissions: ['read'],
          },
        ],
      },
    ],
  };
}

function App(): React.JSX.Element {
  const [isBluetoothEnabled, setIsBluetoothEnabled] = useState(false);
  const [status, setStatus] = useState('Preparing Bluetooth diagnostics...');
  const [capabilities, setCapabilities] = useState<BluetoothCapabilities>();
  const [devices, setDevices] = useState<BLEDevice[]>([]);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isBackgroundSessionActive, setIsBackgroundSessionActive] =
    useState(false);
  const [isMultipeerActive, setIsMultipeerActive] = useState(false);
  const [multipeerPeers, setMultipeerPeers] = useState<MultipeerPeer[]>([]);
  const nextLogId = useRef(1);
  const didAutoRun = useRef(false);
  const connectedDeviceIds = useRef<Set<string>>(new Set());
  const connectingDeviceIds = useRef<Set<string>>(new Set());
  const failedDeviceIds = useRef<Set<string>>(new Set());
  const multipeerPingedPeerIds = useRef<Set<string>>(new Set());

  const addLog = useCallback((message: string, kind: LogKind = 'info') => {
    console.log(`[MunimBluetoothExample] ${message}`);
    setLogs((previous) =>
      [
        { id: nextLogId.current++, message, kind },
        ...previous,
      ].slice(0, 80)
    );
  }, []);

  const checkBluetoothStatus = useCallback(async () => {
    const enabled = await MunimBluetooth.isBluetoothEnabled();
    setIsBluetoothEnabled(enabled);
    setStatus(enabled ? 'Bluetooth is enabled' : 'Bluetooth is disabled');
    addLog(`Bluetooth enabled: ${enabled ? 'yes' : 'no'}`, enabled ? 'success' : 'warning');
    return enabled;
  }, [addLog]);

  const configurePeripheral = useCallback(async () => {
    try {
      MunimBluetooth.stopAdvertising();
    } catch {
      // The smoke test is allowed to start from a clean app launch or a prior run.
    }

    MunimBluetooth.setServices([buildTestService()]);
    MunimBluetooth.startAdvertising({
      serviceUUIDs: [TEST_SERVICE_UUID],
      localName: TEST_LOCAL_NAME,
      advertisingData: {
        completeLocalName: TEST_LOCAL_NAME,
        completeServiceUUIDs128: [TEST_SERVICE_UUID],
      },
    });
    addLog(`Peripheral advertising ${TEST_LOCAL_NAME}`, 'success');
  }, [addLog]);

  const startScanning = useCallback(() => {
    if (!SHOULD_SCAN_FOR_PEERS) {
      setStatus(`${DEVICE_ROLE} advertising for peer centrals`);
      addLog('Peer scanning disabled for this smoke-test role');
      return;
    }

    try {
      MunimBluetooth.stopScan();
    } catch {
      // Safe cleanup for repeated manual runs.
    }

    MunimBluetooth.startScan({
      serviceUUIDs: [TEST_SERVICE_UUID],
      allowDuplicates: false,
      scanMode: 'lowLatency',
    });
    setStatus('Advertising and scanning for peer device');
    addLog('BLE scan started with service filter', 'success');
  }, [addLog]);

  const upsertMultipeerPeer = useCallback((peer: MultipeerPeer) => {
    setMultipeerPeers((previous) =>
      [peer, ...previous.filter((item) => item.id !== peer.id)].slice(0, 12)
    );
  }, []);

  const sendMultipeerPing = useCallback(
    async (peerIds?: string[]) => {
      if (Platform.OS !== 'ios') {
        addLog('Apple Multipeer is iOS/iPadOS only; Android remains on BLE/GATT', 'warning');
        return;
      }

      try {
        await MunimBluetooth.sendMultipeerMessage(
          MULTIPEER_MESSAGE_VALUE,
          peerIds,
          true
        );
        addLog(
          peerIds?.length
            ? `Sent Multipeer ping to ${peerIds.length} peer(s)`
            : 'Broadcast Multipeer ping',
          'success'
        );
      } catch (error) {
        addLog(`Multipeer send failed: ${formatError(error)}`, 'warning');
      }
    },
    [addLog]
  );

  const startMultipeerTransport = useCallback(() => {
    if (Platform.OS !== 'ios') {
      addLog('Apple Multipeer is iOS/iPadOS only; Android uses BLE/GATT', 'warning');
      return;
    }

    try {
      MunimBluetooth.stopMultipeerSession();
    } catch {
      // Safe cleanup before restarting the local peer browser/advertiser.
    }

    try {
      MunimBluetooth.startMultipeerSession({
        serviceType: MULTIPEER_SERVICE_TYPE,
        displayName: TEST_LOCAL_NAME,
        discoveryInfo: [
          { key: 'role', value: DEVICE_ROLE },
          { key: 'transport', value: 'munim-bluetooth' },
        ],
        autoInvite: true,
        autoAcceptInvitations: true,
        inviteTimeout: 30,
        encryptionPreference: 'required',
      });
      setIsMultipeerActive(true);
      addLog('Apple Multipeer session requested', 'success');
    } catch (error) {
      setIsMultipeerActive(false);
      addLog(`Multipeer start failed: ${formatError(error)}`, 'error');
    }
  }, [addLog]);

  const runPeerGattSmoke = useCallback(
    async (device: BLEDevice) => {
      if (
        connectedDeviceIds.current.has(device.id) ||
        connectingDeviceIds.current.has(device.id) ||
        failedDeviceIds.current.has(device.id)
      ) {
        return;
      }

      connectingDeviceIds.current.add(device.id);
      setStatus(`Connecting to ${device.localName ?? device.name ?? device.id}`);
      addLog(`Connecting to ${device.localName ?? device.name ?? device.id}`);

      try {
        await MunimBluetooth.connect(device.id);
        connectingDeviceIds.current.delete(device.id);
        connectedDeviceIds.current.add(device.id);
        addLog(
          `Connected to peer peripheral (${connectedDeviceIds.current.size} active)`,
          'success'
        );

        const services = await MunimBluetooth.discoverServices(device.id);
        const hasService = services.some(
          (service) =>
            service.uuid.toLowerCase() === TEST_SERVICE_UUID.toLowerCase()
        );
        addLog(
          `Discovered ${services.length} service${services.length === 1 ? '' : 's'}`,
          hasService ? 'success' : 'warning'
        );

        const readValue = await MunimBluetooth.readCharacteristic(
          device.id,
          TEST_SERVICE_UUID,
          TEST_CHARACTERISTIC_UUID
        );
        addLog(`Read characteristic value ${readValue.value}`, 'success');

        await MunimBluetooth.writeCharacteristic(
          device.id,
          TEST_SERVICE_UUID,
          TEST_CHARACTERISTIC_UUID,
          UPDATED_VALUE,
          'write'
        );
        addLog('Wrote characteristic value', 'success');

        MunimBluetooth.subscribeToCharacteristic(
          device.id,
          TEST_SERVICE_UUID,
          TEST_CHARACTERISTIC_UUID
        );
        addLog('Subscribed to characteristic notifications', 'success');

        const rssi = await MunimBluetooth.readRSSI(device.id);
        addLog(`RSSI ${Math.round(rssi)} dBm`, 'success');

        if (Platform.OS === 'android') {
          try {
            const mtu = await MunimBluetooth.requestMTU(device.id, 247);
            addLog(`MTU negotiated to ${mtu}`, 'success');
          } catch (error) {
            addLog(`MTU request skipped: ${formatError(error)}`, 'warning');
          }
        }

        const connected = await MunimBluetooth.getConnectedDevices();
        addLog(`Connected devices reported by native layer: ${connected.length}`, 'success');
        setStatus(`${connectedDeviceIds.current.size} peer BLE GATT smoke test(s) passed`);
      } catch (error) {
        try {
          MunimBluetooth.disconnect(device.id);
        } catch {
          // The connection may already be gone after a native timeout.
        }
        connectingDeviceIds.current.delete(device.id);
        connectedDeviceIds.current.delete(device.id);
        failedDeviceIds.current.add(device.id);
        setStatus('Peer BLE GATT smoke failed');
        addLog(formatError(error), 'error');
        startScanning();
      }
    },
    [addLog, startScanning]
  );

  const runDiagnostics = useCallback(async () => {
    setLogs([]);
    setDevices([]);
    setMultipeerPeers([]);
    connectedDeviceIds.current.clear();
    connectingDeviceIds.current.clear();
    failedDeviceIds.current.clear();
    multipeerPingedPeerIds.current.clear();
    setStatus('Running BLE diagnostics...');

    try {
      const permissionGranted = await MunimBluetooth.requestBluetoothPermission([
        'scan',
        'connect',
        'advertise',
      ]);
      addLog(
        `Permission ${permissionGranted ? 'granted' : 'not granted yet'}`,
        permissionGranted ? 'success' : 'warning'
      );

      const enabled = await checkBluetoothStatus();
      const platformCapabilities = await MunimBluetooth.getCapabilities();
      setCapabilities(platformCapabilities);
      addLog(
        `Capabilities central=${platformCapabilities.supportsBleCentral} peripheral=${platformCapabilities.supportsBlePeripheral}`,
        'info'
      );

      if (!enabled) {
        return;
      }

      startMultipeerTransport();
      await configurePeripheral();
      startScanning();
    } catch (error) {
      setStatus('Diagnostics failed to start');
      addLog(formatError(error), 'error');
    }
  }, [
    addLog,
    checkBluetoothStatus,
    configurePeripheral,
    startMultipeerTransport,
    startScanning,
  ]);

  const stopEverything = useCallback(() => {
    try {
      MunimBluetooth.stopScan();
      MunimBluetooth.stopAdvertising();
      MunimBluetooth.stopBackgroundSession();
      MunimBluetooth.stopMultipeerSession();
      const deviceIds = new Set([
        ...Array.from(connectedDeviceIds.current),
        ...Array.from(connectingDeviceIds.current),
      ]);
      deviceIds.forEach((deviceId) => MunimBluetooth.disconnect(deviceId));
    } catch (error) {
      addLog(formatError(error), 'warning');
    } finally {
      connectedDeviceIds.current.clear();
      connectingDeviceIds.current.clear();
      failedDeviceIds.current.clear();
      multipeerPingedPeerIds.current.clear();
      setIsBackgroundSessionActive(false);
      setIsMultipeerActive(false);
      setMultipeerPeers([]);
      setStatus('Bluetooth smoke test stopped');
      addLog('Stopped scan, advertising, connection, and Multipeer');
    }
  }, [addLog]);

  const startBackgroundMode = useCallback(async () => {
    try {
      const permissionGranted = await MunimBluetooth.requestBluetoothPermission([
        'scan',
        'connect',
        'advertise',
      ]);
      if (!permissionGranted) {
        addLog('Background session needs Bluetooth permission', 'warning');
        return;
      }

      await configurePeripheral();
      MunimBluetooth.startBackgroundSession({
        serviceUUIDs: [TEST_SERVICE_UUID],
        localName: TEST_LOCAL_NAME,
        allowDuplicates: false,
        scanMode: 'lowPower',
        androidNotificationTitle: 'Munim Bluetooth nearby mode',
        androidNotificationText: 'Keeping BLE discovery and GATT available',
      });
      setIsBackgroundSessionActive(true);
      setStatus('Background BLE session active');
      addLog('Background BLE session requested', 'success');
    } catch (error) {
      addLog(`Background session failed: ${formatError(error)}`, 'error');
    }
  }, [addLog, configurePeripheral]);

  const stopBackgroundMode = useCallback(() => {
    MunimBluetooth.stopBackgroundSession();
    setIsBackgroundSessionActive(false);
    setStatus('Background BLE session stopped');
    addLog('Background BLE session stopped');
  }, [addLog]);

  useEffect(() => {
    const removeListeners = [
      MunimBluetooth.addEventListener('scanResult', (device) => {
        const label = device.localName ?? device.name ?? device.id;
        addLog(`Scan result: ${label} (${device.rssi ?? 'n/a'} dBm)`);
        setDevices((previous) =>
          [device, ...previous.filter((item) => item.id !== device.id)].slice(
            0,
            20
          )
        );

        if (
          SHOULD_AUTO_CONNECT_TO_PEER &&
          isTestPeer(device) &&
          connectedDeviceIds.current.size + connectingDeviceIds.current.size <
            MAX_AUTO_PEERS
        ) {
          void runPeerGattSmoke(device);
        }
      }),
      MunimBluetooth.addEventListener('advertisingStarted', () => {
        addLog('Advertising callback received', 'success');
      }),
      MunimBluetooth.addEventListener('advertisingStartFailed', (event) => {
        addLog(
          `Advertising failed: ${event.message ?? event.error ?? event.errorCode ?? 'unknown'}`,
          'error'
        );
      }),
      MunimBluetooth.addEventListener('deviceConnected', (event) => {
        addLog(`Device connected: ${event.deviceId}`, 'success');
      }),
      MunimBluetooth.addEventListener('deviceDisconnected', (event) => {
        addLog(`Device disconnected: ${event.deviceId}`, 'warning');
      }),
      MunimBluetooth.addEventListener('characteristicValueChanged', (event) => {
        addLog(
          `Characteristic update ${event.characteristicUUID}: ${event.value}`,
          'success'
        );
      }),
      MunimBluetooth.addEventListener('peripheralReadRequest', (event) => {
        addLog(`Peer read ${event.characteristicUUID}`, 'success');
      }),
      MunimBluetooth.addEventListener('peripheralWriteRequest', (event) => {
        addLog(`Peer wrote ${event.characteristicUUID}: ${event.value}`, 'success');
      }),
      MunimBluetooth.addEventListener('peripheralSubscribed', (event) => {
        addLog(`Peer subscribed ${event.characteristicUUID}`, 'success');
      }),
      MunimBluetooth.addEventListener('scanFailed', (event) => {
        addLog(`Scan failed: ${event.message}`, 'error');
      }),
      MunimBluetooth.addEventListener('backgroundSessionStarted', (event) => {
        setIsBackgroundSessionActive(true);
        addLog(`Background session started on ${event.platform}`, 'success');
      }),
      MunimBluetooth.addEventListener('backgroundSessionStopped', (event) => {
        setIsBackgroundSessionActive(false);
        addLog(`Background session stopped on ${event.platform}`, 'warning');
      }),
      MunimBluetooth.addEventListener('backgroundSessionRestored', (event) => {
        setIsBackgroundSessionActive(true);
        addLog(
          `Background session restored (${event.role ?? 'unknown role'})`,
          'success'
        );
      }),
      MunimBluetooth.addEventListener('backgroundSessionStartFailed', (event) => {
        setIsBackgroundSessionActive(false);
        addLog(`Background session failed: ${event.error}`, 'error');
      }),
      MunimBluetooth.addEventListener('multipeerStarted', (event) => {
        setIsMultipeerActive(true);
        addLog(
          `Multipeer started as ${event.displayName} (${event.serviceType})`,
          'success'
        );
      }),
      MunimBluetooth.addEventListener('multipeerStopped', () => {
        setIsMultipeerActive(false);
        setMultipeerPeers([]);
        multipeerPingedPeerIds.current.clear();
        addLog('Multipeer stopped', 'warning');
      }),
      MunimBluetooth.addEventListener('multipeerStartFailed', (event) => {
        setIsMultipeerActive(false);
        addLog(`Multipeer failed: ${event.error}`, 'error');
      }),
      MunimBluetooth.addEventListener('multipeerPeerFound', (peer) => {
        upsertMultipeerPeer(peer);
        addLog(`Multipeer found ${peer.displayName}`);
      }),
      MunimBluetooth.addEventListener('multipeerPeerLost', (event) => {
        setMultipeerPeers((previous) =>
          previous.filter((peer) => peer.id !== event.peerId)
        );
        multipeerPingedPeerIds.current.delete(event.peerId);
        addLog(`Multipeer lost ${event.peerId}`, 'warning');
      }),
      MunimBluetooth.addEventListener('multipeerPeerStateChanged', (peer) => {
        upsertMultipeerPeer(peer);
        addLog(`Multipeer ${peer.displayName} ${peer.state}`, 'success');

        if (
          peer.state === 'connected' &&
          !multipeerPingedPeerIds.current.has(peer.id)
        ) {
          multipeerPingedPeerIds.current.add(peer.id);
          void sendMultipeerPing([peer.id]);
        }
      }),
      MunimBluetooth.addEventListener('multipeerMessageReceived', (event) => {
        addLog(
          `Multipeer message from ${event.displayName}: ${event.value}`,
          'success'
        );
      }),
    ];

    return () => {
      removeListeners.forEach((remove) => remove());
    };
  }, [addLog, runPeerGattSmoke, sendMultipeerPing, upsertMultipeerPeer]);

  useEffect(() => {
    if (didAutoRun.current) {
      return;
    }

    didAutoRun.current = true;
    void runDiagnostics();
  }, [runDiagnostics]);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Munim Bluetooth</Text>

      <View style={styles.statusBox}>
        <Text style={styles.statusText}>{status}</Text>
        <Text style={styles.statusMeta}>
          {isBluetoothEnabled ? 'Enabled' : 'Disabled'} · {Platform.OS}
        </Text>
      </View>

      <View style={styles.buttonRow}>
        <View style={styles.button}>
          <Button title="Run Smoke" onPress={runDiagnostics} color="#007AFF" />
        </View>
        <View style={styles.button}>
          <Button title="Stop" onPress={stopEverything} color="#FF3B30" />
        </View>
        <View style={styles.button}>
          <Button
            title={isBackgroundSessionActive ? 'Stop BG' : 'Start BG'}
            onPress={
              isBackgroundSessionActive
                ? stopBackgroundMode
                : () => void startBackgroundMode()
            }
            color="#5856D6"
          />
        </View>
      </View>

      <View style={styles.buttonRow}>
        <View style={styles.button}>
          <Button
            title={isMultipeerActive ? 'Restart Mesh' : 'Start Mesh'}
            onPress={startMultipeerTransport}
            color="#0A84FF"
          />
        </View>
        <View style={styles.button}>
          <Button
            title="Ping Mesh"
            onPress={() => void sendMultipeerPing()}
            color="#30D158"
          />
        </View>
      </View>

      {capabilities ? (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Capabilities</Text>
          <Text style={styles.monoText}>
            central={String(capabilities.supportsBleCentral)} peripheral=
            {String(capabilities.supportsBlePeripheral)} descriptors=
            {String(capabilities.supportsDescriptors)} l2cap=
            {String(capabilities.supportsL2cap)} classic=
            {String(capabilities.supportsClassicBluetooth)} multipeer=
            {String(capabilities.supportsMultipeerConnectivity)}
          </Text>
        </View>
      ) : null}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Devices</Text>
        {devices.length === 0 ? (
          <Text style={styles.emptyText}>No peer devices found yet.</Text>
        ) : (
          devices.map((device) => (
            <View key={device.id} style={styles.deviceRow}>
              <View style={styles.deviceText}>
                <Text style={styles.deviceName}>
                  {device.localName ?? device.name ?? 'Unnamed device'}
                </Text>
                <Text style={styles.deviceId}>{device.id}</Text>
              </View>
              <Button
                title="Test"
                onPress={() => void runPeerGattSmoke(device)}
                color="#34C759"
              />
            </View>
          ))
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Multipeer</Text>
        {multipeerPeers.length === 0 ? (
          <Text style={styles.emptyText}>No Apple Multipeer peers found yet.</Text>
        ) : (
          multipeerPeers.map((peer) => (
            <View key={peer.id} style={styles.deviceRow}>
              <View style={styles.deviceText}>
                <Text style={styles.deviceName}>{peer.displayName}</Text>
                <Text style={styles.deviceId}>
                  {peer.state} · {peer.id}
                </Text>
              </View>
              <Button
                title="Ping"
                onPress={() => void sendMultipeerPing([peer.id])}
                color="#34C759"
              />
            </View>
          ))
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Log</Text>
        {logs.map((entry) => (
          <Text key={entry.id} style={[styles.logText, styles[entry.kind]]}>
            {entry.message}
          </Text>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    padding: 20,
    paddingTop: 40,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#000',
    marginBottom: 24,
    textAlign: 'center',
  },
  statusBox: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#007AFF',
  },
  statusText: {
    fontSize: 16,
    color: '#111',
    textAlign: 'center',
  },
  statusMeta: {
    marginTop: 6,
    color: '#666',
    fontSize: 13,
    textAlign: 'center',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 18,
  },
  button: {
    flex: 1,
    borderRadius: 8,
    overflow: 'hidden',
  },
  section: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 14,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#111',
    marginBottom: 10,
  },
  monoText: {
    color: '#333',
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace' }),
    fontSize: 12,
    lineHeight: 18,
  },
  emptyText: {
    color: '#666',
    fontSize: 14,
  },
  deviceRow: {
    alignItems: 'center',
    borderTopColor: '#e7e7e7',
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 12,
    paddingVertical: 10,
  },
  deviceText: {
    flex: 1,
  },
  deviceName: {
    color: '#111',
    fontSize: 14,
    fontWeight: '700',
  },
  deviceId: {
    color: '#666',
    fontSize: 11,
    marginTop: 2,
  },
  logText: {
    borderTopColor: '#ececec',
    borderTopWidth: StyleSheet.hairlineWidth,
    color: '#333',
    fontSize: 12,
    lineHeight: 18,
    paddingVertical: 6,
  },
  info: {
    color: '#333',
  },
  success: {
    color: '#137333',
  },
  warning: {
    color: '#9a6700',
  },
  error: {
    color: '#b3261e',
  },
});

export default App;
