import { NitroModules } from 'react-native-nitro-modules'
import type { MunimBluetooth as MunimBluetoothSpec } from './specs/munim-bluetooth.nitro'

export const MunimBluetooth =
  NitroModules.createHybridObject<MunimBluetoothSpec>('MunimBluetooth')