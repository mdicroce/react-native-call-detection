//
//  CallDetectionManager.m
//
//
//  Created by Pritesh Nandgaonkar on 16/06/17.
//  Updated by Doug Watkins for Inside Real Estate on 31/07/19
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "CallDetectionManager.h"
#import <CallKit/CallKit.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface CallDetectionManager () <CXCallObserverDelegate>

@property(strong, nonatomic) CXCallObserver *callObserver;
@property(assign, nonatomic) BOOL hasSentInitialState;
@property(assign, nonatomic) BOOL isObserving;

@end
@implementation CallDetectionManager

- (NSArray<NSString *> *)supportedEvents {
  return @[ @"PhoneCallStateUpdate" ];
}

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(startListener) {
  // Setup call tracking
  if (self.callObserver == nil) {
    self.callObserver = [[CXCallObserver alloc] init];
    __typeof(self) weakSelf = self;
    [self.callObserver setDelegate:weakSelf queue:nil];
  }
}

RCT_EXPORT_METHOD(stopListener) {
  // Setup call tracking
  self.callObserver = nil;
}

RCT_EXPORT_METHOD(getCurrentState:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  // Ensure we have an observer to read current calls
  if (self.callObserver == nil) {
    self.callObserver = [[CXCallObserver alloc] init];
    __typeof(self) weakSelf = self;
    [self.callObserver setDelegate:weakSelf queue:nil];
  }

  @try {
    NSString *state = @"Disconnected";
    if (self.callObserver.calls.count > 0) {
      // Use the most recent call for state reporting
      CXCall *call = [self.callObserver.calls lastObject];
      if (call.hasEnded == true) {
        state = @"Disconnected";
      } else if (call.hasConnected == true) {
        state = @"Connected";
      } else if (call.isOutgoing == true) {
        state = @"Dialing";
      } else {
        state = @"Incoming";
      }
    }
    resolve(state);
  } @catch (NSException *exception) {
    reject(@"call_state_error", exception.reason, nil);
  }
}

RCT_EXPORT_METHOD(requestCurrentState) {
  // Ensure we have an observer to read current calls
  if (self.callObserver == nil) {
    self.callObserver = [[CXCallObserver alloc] init];
    __typeof(self) weakSelf = self;
    [self.callObserver setDelegate:weakSelf queue:nil];
  }
  [self reportCurrentState];
}

- (void)startObserving {
  self.isObserving = YES;
  // Ensure observer exists
  if (self.callObserver == nil) {
    self.callObserver = [[CXCallObserver alloc] init];
    __typeof(self) weakSelf = self;
    [self.callObserver setDelegate:weakSelf queue:nil];
  }
  // Emit the initial state now that JS is listening
  [self reportCurrentState];
}

- (void)stopObserving {
  self.isObserving = NO;
}

- (void)callObserver:(CXCallObserver *)callObserver callChanged:(CXCall *)call {
  if (!self.isObserving) { return; }
  if (call.hasEnded == true) {
    [self sendEventWithName:@"PhoneCallStateUpdate" body:@"Disconnected"];
  } else if (call.hasConnected == true) {
    [self sendEventWithName:@"PhoneCallStateUpdate" body:@"Connected"];
  } else if (call.isOutgoing == true) {
    [self sendEventWithName:@"PhoneCallStateUpdate" body:@"Dialing"];
  } else {
    [self sendEventWithName:@"PhoneCallStateUpdate" body:@"Incoming"];
  }
}

- (void)reportCurrentState {
  if (!self.isObserving) { return; }
  if (self.callObserver.calls.count > 0) {
    for (CXCall *call in self.callObserver.calls) {
      [self callObserver:self.callObserver callChanged:call];
    }
  } else {
    [self sendEventWithName:@"PhoneCallStateUpdate" body:@"Disconnected"];
  }
}

@end

