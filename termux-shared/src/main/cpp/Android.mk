LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_LDLIBS := -llog
LOCAL_MODULE := local-socket
LOCAL_SRC_FILES := local-socket.cpp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := termux-prefix-remap
LOCAL_SRC_FILES := prefix-remap/termux-prefix-remap.c
# Minimal dependencies — do NOT link liblog (the shim may be loaded into
# non-Android (glibc) processes).  -ldl is the only safe dependency.
LOCAL_LDLIBS := -ldl
LOCAL_CFLAGS := -O2 -fvisibility=default -Wall -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
