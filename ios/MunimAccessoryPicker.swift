//
//  MunimAccessoryPicker.swift
//  munim-bluetooth
//
//  showDevicePicker() on iOS 18+: AccessorySetupKit. The system picker
//  finds the accessory and grants this app access to it without the broad
//  Bluetooth permission prompt; the accessory's bluetoothIdentifier is the
//  CoreBluetooth peripheral identifier, so connect(id) works afterwards.
//

import Foundation
import CoreBluetooth
import NitroModules
#if canImport(AccessorySetupKit) && os(iOS)
import AccessorySetupKit
import UIKit
#endif

final class MunimAccessoryPicker {
    /// All session state is confined to the main queue, where
    /// AccessorySetupKit presents its UI.
    private let callbackQueue: DispatchQueue
    private var session: AnyObject?
    private var activated = false
    private var whenActivated: [() -> Void] = []
    private var pendingPick: Promise<AssociatedDevice>?
    private var dismissWorkItem: DispatchWorkItem?

    init(queue: DispatchQueue) {
        self.callbackQueue = queue
    }

    /// True on iOS 18+ when the app declares Bluetooth in
    /// NSAccessorySetupKitSupports (AccessorySetupKit refuses otherwise).
    static var isSupported: Bool {
#if canImport(AccessorySetupKit) && os(iOS)
        if #available(iOS 18.0, *) {
            let supports = Bundle.main.object(forInfoDictionaryKey: "NSAccessorySetupKitSupports") as? [String]
            return supports?.contains("Bluetooth") == true
        }
#endif
        return false
    }

    func show(options: DevicePickerOptions) -> Promise<AssociatedDevice> {
        let promise = Promise<AssociatedDevice>()
#if canImport(AccessorySetupKit) && os(iOS)
        guard #available(iOS 18.0, *), Self.isSupported else {
            promise.reject(withError: Self.unsupportedError())
            return promise
        }
        let displayName = options.displayName.flatMap { $0.isEmpty ? nil : $0 } ?? "Accessory"
        var filters: [(CBUUID, String?)] = []
        for filter in options.filters {
            guard let uuid = filter.serviceUUID, !uuid.isEmpty else {
                promise.reject(withError: Self.error(400, "AccessorySetupKit needs a serviceUUID in every filter (also list it in NSAccessorySetupBluetoothServices)"))
                return promise
            }
            filters.append((CBUUID(string: uuid), filter.namePrefix.flatMap { $0.isEmpty ? nil : $0 }))
        }
        guard !filters.isEmpty else {
            promise.reject(withError: Self.error(400, "showDevicePicker needs at least one filter"))
            return promise
        }

        DispatchQueue.main.async {
            guard self.pendingPick == nil else {
                promise.reject(withError: Self.error(409, "A device picker is already showing"))
                return
            }
            self.pendingPick = promise
            self.withActiveSession { session in
                guard let session = session as? ASAccessorySession else { return }
                let image = UIImage(systemName: "dot.radiowaves.left.and.right") ?? UIImage()
                let items = filters.map { filter -> ASPickerDisplayItem in
                    let (serviceUUID, nameSubstring) = filter
                    let descriptor = ASDiscoveryDescriptor()
                    descriptor.bluetoothServiceUUID = serviceUUID
                    if let nameSubstring {
                        descriptor.bluetoothNameSubstring = nameSubstring
                    }
                    return ASPickerDisplayItem(name: displayName, productImage: image, descriptor: descriptor)
                }
                session.showPicker(for: items) { error in
                    guard let error else { return }
                    DispatchQueue.main.async {
                        self.settlePick { $0.reject(withError: error) }
                    }
                }
            }
        }
#else
        promise.reject(withError: Self.unsupportedError())
#endif
        return promise
    }

    func associations() -> Promise<[AssociatedDevice]> {
        let promise = Promise<[AssociatedDevice]>()
#if canImport(AccessorySetupKit) && os(iOS)
        guard #available(iOS 18.0, *), Self.isSupported else {
            promise.resolve(withResult: [])
            return promise
        }
        DispatchQueue.main.async {
            self.withActiveSession { session in
                let accessories = (session as? ASAccessorySession)?.accessories ?? []
                promise.resolve(withResult: accessories.map { Self.device(from: $0) })
            }
        }
#else
        promise.resolve(withResult: [])
#endif
        return promise
    }

    func remove(associationId: String) -> Promise<Void> {
        let promise = Promise<Void>()
#if canImport(AccessorySetupKit) && os(iOS)
        guard #available(iOS 18.0, *), Self.isSupported else {
            promise.reject(withError: Self.unsupportedError())
            return promise
        }
        DispatchQueue.main.async {
            self.withActiveSession { session in
                guard let session = session as? ASAccessorySession,
                      let accessory = session.accessories.first(where: { Self.associationId(of: $0) == associationId }) else {
                    promise.reject(withError: Self.error(404, "No associated accessory with id \(associationId)"))
                    return
                }
                session.removeAccessory(accessory) { error in
                    if let error {
                        promise.reject(withError: error)
                    } else {
                        promise.resolve(withResult: ())
                    }
                }
            }
        }
#else
        promise.reject(withError: Self.unsupportedError())
#endif
        return promise
    }

    // MARK: - Session

#if canImport(AccessorySetupKit) && os(iOS)
    /// Runs `body` with an activated session. Main queue only.
    @available(iOS 18.0, *)
    private func withActiveSession(_ body: @escaping (AnyObject?) -> Void) {
        if let session, activated {
            body(session)
            return
        }
        whenActivated.append { [weak self] in body(self?.session) }
        guard session == nil else { return }
        let newSession = ASAccessorySession()
        session = newSession
        newSession.activate(on: DispatchQueue.main) { [weak self] event in
            self?.handle(event)
        }
    }

    @available(iOS 18.0, *)
    private func handle(_ event: ASAccessoryEvent) {
        switch event.eventType {
        case .activated:
            activated = true
            let waiting = whenActivated
            whenActivated.removeAll()
            waiting.forEach { $0() }
        case .accessoryAdded:
            if let accessory = event.accessory {
                let device = Self.device(from: accessory)
                settlePick { $0.resolve(withResult: device) }
            }
        case .pickerDidDismiss:
            // accessoryAdded can trail the dismissal slightly; give it a
            // moment before treating the dismissal as a cancel.
            dismissWorkItem?.cancel()
            let workItem = DispatchWorkItem { [weak self] in
                self?.settlePick { $0.reject(withError: Self.error(499, "Device picker was cancelled")) }
            }
            dismissWorkItem = workItem
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0, execute: workItem)
        case .invalidated:
            activated = false
            session = nil
            let waiting = whenActivated
            whenActivated.removeAll()
            waiting.forEach { $0() }
            let failure: Error
            if let error = event.error {
                failure = error
            } else {
                failure = Self.error(500, "AccessorySetupKit session was invalidated")
            }
            settlePick { $0.reject(withError: failure) }
        default:
            break
        }
    }

    @available(iOS 18.0, *)
    private static func associationId(of accessory: ASAccessory) -> String {
        accessory.bluetoothIdentifier?.uuidString ?? accessory.displayName
    }

    @available(iOS 18.0, *)
    private static func device(from accessory: ASAccessory) -> AssociatedDevice {
        AssociatedDevice(
            id: accessory.bluetoothIdentifier?.uuidString ?? "",
            name: accessory.displayName,
            associationId: associationId(of: accessory)
        )
    }
#endif

    /// Main queue only.
    private func settlePick(_ action: (Promise<AssociatedDevice>) -> Void) {
        dismissWorkItem?.cancel()
        dismissWorkItem = nil
        guard let promise = pendingPick else { return }
        pendingPick = nil
        action(promise)
    }

    private static func unsupportedError() -> NSError {
        error(501, "showDevicePicker needs iOS 18+ (AccessorySetupKit) and \"Bluetooth\" in NSAccessorySetupKitSupports; see the config plugin's accessorySetup option")
    }

    private static func error(_ code: Int, _ message: String) -> NSError {
        NSError(domain: "MunimBluetooth", code: code, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
