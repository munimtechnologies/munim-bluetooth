#include <jni.h>
#include "MunimBluetoothOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::munimbluetooth::initialize(vm);
}
