//
//  MunimBluetoothEventEmitter.m
//  munim-bluetooth
//
//  Objective-C bridge for event emitter
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(MunimBluetoothEventEmitter, RCTEventEmitter)

RCT_EXTERN_METHOD(supportedEvents)

@end
