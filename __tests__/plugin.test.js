const { applyInfoPlist, applyAndroidManifest } = require('../app.plugin')

describe('config plugin: Info.plist', () => {
  it('adds the usage strings, background modes and Bonjour services by default', () => {
    const plist = applyInfoPlist({})
    expect(plist.NSBluetoothAlwaysUsageDescription).toEqual(expect.any(String))
    expect(plist.UIBackgroundModes).toEqual(
      expect.arrayContaining(['bluetooth-central', 'bluetooth-peripheral'])
    )
    expect(plist.NSBonjourServices).toEqual(
      expect.arrayContaining([
        '_munim-mesh._tcp',
        '_munim-mesh._udp',
        '_munimbluetooth._tcp',
      ])
    )
    expect(plist.NSAccessorySetupKitSupports).toBeUndefined()
  })

  it('declares AccessorySetupKit services and names when asked', () => {
    const plist = applyInfoPlist(
      { NSAccessorySetupBluetoothServices: ['180D'] },
      {
        accessorySetup: {
          bluetoothServices: ['0000feed-0000-1000-8000-00805f9b34fb', '180d'],
          bluetoothNames: ['Munim'],
        },
      }
    )
    expect(plist.NSAccessorySetupKitSupports).toEqual(['Bluetooth'])
    expect(plist.NSAccessorySetupBluetoothServices).toEqual([
      '180D',
      '0000FEED-0000-1000-8000-00805F9B34FB',
    ])
    expect(plist.NSAccessorySetupBluetoothNames).toEqual(['Munim'])
  })

  it('rejects an accessorySetup without services', () => {
    expect(() =>
      applyInfoPlist({}, { accessorySetup: { bluetoothServices: [] } })
    ).toThrow(TypeError)
  })

  it('keeps existing values and skips background modes when disabled', () => {
    const plist = applyInfoPlist(
      { NSBluetoothAlwaysUsageDescription: 'mine', UIBackgroundModes: ['audio'] },
      { bluetoothBackground: false, multipeerServiceTypes: false }
    )
    expect(plist.NSBluetoothAlwaysUsageDescription).toBe('mine')
    expect(plist.UIBackgroundModes).toEqual(['audio'])
    expect(plist.NSBonjourServices).toBeUndefined()
  })
})

describe('config plugin: AndroidManifest', () => {
  const names = (entries) => (entries ?? []).map((entry) => entry.$['android:name'])

  it('adds scan/connect permissions with neverForLocation and optional features', () => {
    const manifest = { application: [{}] }
    applyAndroidManifest(manifest)
    expect(names(manifest['uses-permission'])).toEqual(
      expect.arrayContaining([
        'android.permission.BLUETOOTH_SCAN',
        'android.permission.BLUETOOTH_CONNECT',
      ])
    )
    const scan = manifest['uses-permission'].find(
      (entry) => entry.$['android:name'] === 'android.permission.BLUETOOTH_SCAN'
    )
    expect(scan.$['android:usesPermissionFlags']).toBe('neverForLocation')
    const features = manifest['uses-feature']
    expect(names(features)).toEqual(
      expect.arrayContaining(['android.software.companion_device_setup'])
    )
    features.forEach((feature) =>
      expect(feature.$['android:required']).toBe('false')
    )
    expect(names(manifest.application[0].service)).toEqual([
      'com.munimbluetooth.MunimBluetoothBackgroundService',
    ])
  })

  it('is idempotent', () => {
    const manifest = { application: [{}] }
    applyAndroidManifest(manifest, { androidBluetoothPermissions: ['advertise'] })
    const count = manifest['uses-permission'].length
    applyAndroidManifest(manifest, { androidBluetoothPermissions: ['advertise'] })
    expect(manifest['uses-permission']).toHaveLength(count)
  })

  it('rejects unknown permission capabilities', () => {
    expect(() =>
      applyAndroidManifest({}, { androidBluetoothPermissions: ['teleport'] })
    ).toThrow(TypeError)
  })
})
