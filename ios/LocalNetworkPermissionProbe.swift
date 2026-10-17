//
//  LocalNetworkPermissionProbe.swift
//  munim-bluetooth
//
//  Apple Multipeer Connectivity needs Local Network access. When it is denied,
//  MCNearbyServiceBrowser finds no peers and reports no error, so this probe
//  publishes and browses a private Bonjour service to learn the state (and to
//  show the prompt on first use). Ported from munim-wifi.
//

import Foundation
import Network
#if canImport(UIKit)
import UIKit
#endif

final class LocalNetworkPermissionProbe {
  /// Must be listed in NSBonjourServices (the config plugin adds it).
  static let serviceType = "_munimbluetooth._tcp"
  private static let probedKey = "munim-bluetooth.local-network-probed"

  private let queue = DispatchQueue(label: "com.munimbluetooth.local-network-probe")
  private let serviceName = "munim-bluetooth-\(UUID().uuidString.prefix(8))"
  private var listener: NWListener?
  private var browser: NWBrowser?
  private var deniedSeen = false
  private var finished = false
  private var activeObserver: NSObjectProtocol?
  private var completion: ((LocalNetworkPermissionState) -> Void)?
  private var retainSelf: LocalNetworkPermissionProbe?

  func run(timeout: TimeInterval, completion: @escaping (LocalNetworkPermissionState) -> Void) {
    self.completion = completion
    retainSelf = self
    // A policy-denied browse right after the first prompt only means "not
    // answered yet"; on later launches it means the user said no.
    let probedBefore = UserDefaults.standard.bool(forKey: Self.probedKey)
    UserDefaults.standard.set(true, forKey: Self.probedKey)

    queue.async {
      do {
        let listener = try NWListener(using: .tcp)
        listener.service = NWListener.Service(name: self.serviceName, type: Self.serviceType)
        listener.newConnectionHandler = { $0.cancel() }
        listener.stateUpdateHandler = { [weak self] state in
          if case .failed = state { self?.finish(.denied) }
        }
        self.listener = listener
        listener.start(queue: self.queue)
      } catch {
        self.finish(.unavailable)
        return
      }

      let parameters = NWParameters()
      parameters.includePeerToPeer = false
      let browser = NWBrowser(for: .bonjour(type: Self.serviceType, domain: nil), using: parameters)
      browser.stateUpdateHandler = { [weak self] state in
        guard let self else { return }
        switch state {
        case .waiting(let error), .failed(let error):
          if case let .dns(code) = error, code == DNSServiceErrorType(kDNSServiceErr_PolicyDenied) {
            self.deniedSeen = true
            if probedBefore { self.finish(.denied) }
          }
        default:
          break
        }
      }
      browser.browseResultsChangedHandler = { [weak self] results, _ in
        guard let self else { return }
        let seen = results.contains { result in
          if case let .service(name, _, _, _) = result.endpoint { return name == self.serviceName }
          return false
        }
        if seen { self.finish(.granted) }
      }
      self.browser = browser
      browser.start(queue: self.queue)

      self.queue.asyncAfter(deadline: .now() + timeout) {
        self.finish(self.deniedSeen ? .denied : .notdetermined)
      }
    }

    #if canImport(UIKit)
    DispatchQueue.main.async {
      self.activeObserver = NotificationCenter.default.addObserver(
        forName: UIApplication.didBecomeActiveNotification,
        object: nil,
        queue: nil
      ) { [weak self] _ in
        // The alert was dismissed. Give a granted browse a moment to report.
        self?.queue.asyncAfter(deadline: .now() + 1.5) {
          guard let self, self.deniedSeen else { return }
          self.finish(.denied)
        }
      }
    }
    #endif
  }

  private func finish(_ state: LocalNetworkPermissionState) {
    queue.async {
      guard !self.finished else { return }
      self.finished = true
      self.browser?.cancel()
      self.listener?.cancel()
      self.browser = nil
      self.listener = nil
      let completion = self.completion
      self.completion = nil
      completion?(state)
      DispatchQueue.main.async {
        if let observer = self.activeObserver {
          NotificationCenter.default.removeObserver(observer)
        }
        self.activeObserver = nil
        self.retainSelf = nil
      }
    }
  }
}
