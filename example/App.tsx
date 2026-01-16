import React, { useEffect, useState } from 'react';
import { Text, View, StyleSheet, Button, ScrollView } from 'react-native';
import * as MunimBluetooth from 'munim-bluetooth';

function App(): React.JSX.Element {
  const [isBluetoothEnabled, setIsBluetoothEnabled] = useState(false);
  const [status, setStatus] = useState('Checking Bluetooth...');

  useEffect(() => {
    checkBluetoothStatus();
  }, []);

  const checkBluetoothStatus = async () => {
    try {
      const enabled = await MunimBluetooth.isBluetoothEnabled();
      setIsBluetoothEnabled(enabled);
      setStatus(enabled ? 'Bluetooth is Enabled ✓' : 'Bluetooth is Disabled ✗');
    } catch (error) {
      setStatus('Error checking Bluetooth');
      console.error(error);
    }
  };

  const requestPermission = async () => {
    try {
      const granted = await MunimBluetooth.requestBluetoothPermission();
      setStatus(granted ? 'Permission Granted ✓' : 'Permission Denied ✗');
    } catch (error) {
      setStatus('Error requesting permission');
      console.error(error);
    }
  };

  const startScanning = async () => {
    try {
      setStatus('Scanning for devices...');
      MunimBluetooth.startScan({
        allowDuplicates: false,
        scanMode: 'lowLatency',
      });
      setStatus('Scanning active...');
    } catch (error) {
      setStatus('Error starting scan');
      console.error(error);
    }
  };

  const stopScanning = async () => {
    try {
      MunimBluetooth.stopScan();
      setStatus('Scan stopped');
    } catch (error) {
      setStatus('Error stopping scan');
      console.error(error);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Munim Bluetooth</Text>

        <View style={styles.statusBox}>
          <Text style={styles.statusText}>{status}</Text>
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Check Bluetooth"
            onPress={checkBluetoothStatus}
            color="#007AFF"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Request Permission"
            onPress={requestPermission}
            color="#34C759"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Start Scanning"
            onPress={startScanning}
            color="#FF9500"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Stop Scanning"
            onPress={stopScanning}
            color="#FF3B30"
          />
        </View>
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
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#007AFF',
  },
  statusText: {
    fontSize: 16,
    color: '#333',
    textAlign: 'center',
  },
  buttonContainer: {
    marginBottom: 12,
    borderRadius: 8,
    overflow: 'hidden',
  },
});

export default App;
