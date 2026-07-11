const { withAndroidManifest, withInfoPlist } = require('@expo/config-plugins');

const DEFAULT_ANDROID_BLUETOOTH_PERMISSIONS = ['scan', 'connect'];
const ANDROID_BLUETOOTH_PERMISSIONS = {
  scan: [
    { name: 'android.permission.BLUETOOTH', maxSdkVersion: '30' },
    { name: 'android.permission.BLUETOOTH_ADMIN', maxSdkVersion: '30' },
    { name: 'android.permission.ACCESS_FINE_LOCATION', maxSdkVersion: '30' },
    { name: 'android.permission.BLUETOOTH_SCAN' },
    { name: 'android.permission.BLUETOOTH_CONNECT' },
  ],
  connect: [
    { name: 'android.permission.BLUETOOTH', maxSdkVersion: '30' },
    { name: 'android.permission.BLUETOOTH_CONNECT' },
  ],
  advertise: [
    { name: 'android.permission.BLUETOOTH', maxSdkVersion: '30' },
    { name: 'android.permission.BLUETOOTH_ADMIN', maxSdkVersion: '30' },
    { name: 'android.permission.BLUETOOTH_ADVERTISE' },
    { name: 'android.permission.BLUETOOTH_CONNECT' },
  ],
};

const ANDROID_SERVICE_PERMISSIONS = [
  { name: 'android.permission.FOREGROUND_SERVICE' },
  { name: 'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE' },
  { name: 'android.permission.FOREGROUND_SERVICE_LOCATION' },
  { name: 'android.permission.POST_NOTIFICATIONS' },
];

const IOS_BACKGROUND_MODES = ['bluetooth-central', 'bluetooth-peripheral'];
const DEFAULT_MULTIPEER_SERVICE_TYPES = ['munim-mesh'];

function ensureArray(parent, key) {
  if (!parent[key]) {
    parent[key] = [];
  }
  return parent[key];
}

function ensureAndroidPermission(manifest, permission) {
  const permissions = ensureArray(manifest, 'uses-permission');
  const exists = permissions.some(
    (entry) => entry.$?.['android:name'] === permission.name
  );

  if (!exists) {
    const attributes = { 'android:name': permission.name };
    if (permission.maxSdkVersion) {
      attributes['android:maxSdkVersion'] = permission.maxSdkVersion;
    }
    permissions.push({ $: attributes });
  }
}

function normalizeAndroidBluetoothPermissions(value) {
  if (value === false) {
    return [];
  }

  const permissions = value ?? DEFAULT_ANDROID_BLUETOOTH_PERMISSIONS;
  if (!Array.isArray(permissions)) {
    throw new TypeError('androidBluetoothPermissions must be an array or false');
  }

  const normalized = Array.from(new Set(permissions));
  normalized.forEach((permission) => {
    if (!ANDROID_BLUETOOTH_PERMISSIONS[permission]) {
      throw new TypeError(
        `Unsupported Android Bluetooth permission capability: ${permission}`
      );
    }
  });
  return normalized;
}

function ensureAndroidFeature(manifest, featureName, required) {
  const features = ensureArray(manifest, 'uses-feature');
  const exists = features.some(
    (entry) => entry.$?.['android:name'] === featureName
  );

  if (!exists) {
    features.push({
      $: {
        'android:name': featureName,
        'android:required': String(required),
      },
    });
  }
}

function ensureBackgroundService(manifest) {
  const application = manifest.application?.[0];
  if (!application) {
    return;
  }

  const services = ensureArray(application, 'service');
  const serviceName = 'com.munimbluetooth.MunimBluetoothBackgroundService';
  const exists = services.some(
    (entry) => entry.$?.['android:name'] === serviceName
  );

  if (!exists) {
    services.push({
      $: {
        'android:name': serviceName,
        'android:enabled': 'true',
        'android:exported': 'false',
        'android:foregroundServiceType': 'connectedDevice|location',
      },
    });
  }
}

function normalizeMultipeerServiceTypes(value) {
  if (value === false) {
    return [];
  }

  const serviceTypes = Array.isArray(value)
    ? value
    : DEFAULT_MULTIPEER_SERVICE_TYPES;

  return serviceTypes
    .map((serviceType) => String(serviceType).trim())
    .filter(Boolean)
    .map((serviceType) =>
      serviceType
        .replace(/^_/, '')
        .replace(/\._tcp$/, '')
        .replace(/^_/, '')
    );
}

function bonjourServiceName(serviceType) {
  return `_${serviceType}._tcp`;
}

function withMunimBluetooth(config, options = {}) {
  const bluetoothBackground =
    options.bluetoothBackground === undefined ? true : options.bluetoothBackground;
  const multipeerServiceTypes = normalizeMultipeerServiceTypes(
    options.multipeerServiceTypes
  );
  const androidBluetoothPermissions = normalizeAndroidBluetoothPermissions(
    options.androidBluetoothPermissions
  );

  config = withInfoPlist(config, (pluginConfig) => {
    const infoPlist = pluginConfig.modResults;
    infoPlist.NSBluetoothAlwaysUsageDescription =
      options.bluetoothAlwaysUsageDescription ??
      infoPlist.NSBluetoothAlwaysUsageDescription ??
      'This app uses Bluetooth to scan for, connect to, and communicate with nearby devices.';
    infoPlist.NSBluetoothPeripheralUsageDescription =
      options.bluetoothPeripheralUsageDescription ??
      infoPlist.NSBluetoothPeripheralUsageDescription ??
      'This app uses Bluetooth peripheral mode to advertise services and exchange data with nearby devices.';

    if (multipeerServiceTypes.length > 0) {
      infoPlist.NSLocalNetworkUsageDescription =
        options.localNetworkUsageDescription ??
        infoPlist.NSLocalNetworkUsageDescription ??
        'This app uses the local network to discover and communicate with nearby peer devices.';

      const bonjourServices = new Set(infoPlist.NSBonjourServices ?? []);
      multipeerServiceTypes
        .map(bonjourServiceName)
        .forEach((serviceName) => bonjourServices.add(serviceName));
      infoPlist.NSBonjourServices = Array.from(bonjourServices);
    }

    if (bluetoothBackground) {
      const modes = new Set(infoPlist.UIBackgroundModes ?? []);
      IOS_BACKGROUND_MODES.forEach((mode) => modes.add(mode));
      infoPlist.UIBackgroundModes = Array.from(modes);
    }

    return pluginConfig;
  });

  return withAndroidManifest(config, (pluginConfig) => {
    const manifest = pluginConfig.modResults.manifest;
    androidBluetoothPermissions
      .flatMap((permission) => ANDROID_BLUETOOTH_PERMISSIONS[permission])
      .concat(ANDROID_SERVICE_PERMISSIONS)
      .forEach((permission) => ensureAndroidPermission(manifest, permission));
    ensureAndroidFeature(manifest, 'android.hardware.bluetooth', false);
    ensureAndroidFeature(manifest, 'android.hardware.bluetooth_le', true);
    ensureBackgroundService(manifest);
    return pluginConfig;
  });
}

module.exports = withMunimBluetooth;
module.exports.default = withMunimBluetooth;
