/*
* @providesModule react-native-call-detection
*/
import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  PermissionsAndroid
} from 'react-native'
export const permissionDenied = 'PERMISSION DENIED'



const NativeCallDetector = NativeModules.CallDetectionManager
const NativeCallDetectorAndroid = NativeModules.CallDetectionManagerAndroid

var CallStateUpdateActionModule = require('./CallStateUpdateActionModule')
// BatchedBridge.registerCallableModule('CallStateUpdateActionModule', CallStateUpdateActionModule)

// https://stackoverflow.com/questions/13154445/how-to-get-phone-number-from-an-incoming-call : Amjad Alwareh's answer.
const requestPermissionsAndroid = (permissionMessage) => {
  const requiredPermission = Platform.constants.Release >= 9
    ? PermissionsAndroid.PERMISSIONS.READ_CALL_LOG
    : PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE
  return PermissionsAndroid.check(requiredPermission)
    .then((gotPermission) => gotPermission
      ? true
      : PermissionsAndroid.request(requiredPermission, permissionMessage)
        .then((result) => result === PermissionsAndroid.RESULTS.GRANTED)
    )
}

class CallDetectorManager {

  subscription;
  callback
  constructor(callback, readPhoneNumberAndroid = false, permissionDeniedCallback = () => { }, permissionMessage = {
    title: 'Phone State Permission',
    message: 'This app needs access to your phone state in order to react and/or to adapt to incoming calls.'
  }) {
    this.callback = callback
    if (Platform.OS === 'ios') {
      NativeCallDetector && NativeCallDetector.startListener()
      this.subscription = new NativeEventEmitter(NativeCallDetector)
      this.subscription.addListener('PhoneCallStateUpdate', callback);
    }
    else {
      if (NativeCallDetectorAndroid) {
        if (readPhoneNumberAndroid) {
          requestPermissionsAndroid(permissionMessage)
            .then((permissionGrantedReadState) => {
              if (!permissionGrantedReadState) {
                permissionDeniedCallback(permissionDenied)
              }
            })
            .catch(permissionDeniedCallback)
        }
        NativeCallDetectorAndroid.startListener()
        
        // Use NativeEventEmitter for Android too
        this.subscription = new NativeEventEmitter(NativeCallDetectorAndroid)
        this.subscription.addListener('PhoneCallStateUpdate', (event) => {
            // Android might send the event differently, so we normalize the callback
            // The Java module sends: {state: '...', phoneNumber: '...'}
            // The callback expects (event, phoneNumber) which matches "state"
            if (event && event.state) {
                callback(event.state, event.phoneNumber)
            } else {
                // Fallback for older patterns just in case
                callback(event)
            }
        })
      }
    }
  }

  dispose() {
    NativeCallDetector && NativeCallDetector.stopListener()
    NativeCallDetectorAndroid && NativeCallDetectorAndroid.stopListener()
    CallStateUpdateActionModule.callback = undefined
    if (this.subscription) {
      this.subscription.removeAllListeners('PhoneCallStateUpdate');
      this.subscription = undefined
    }
  }
}
export default module.exports = CallDetectorManager;
