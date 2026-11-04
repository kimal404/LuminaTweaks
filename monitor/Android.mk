# Copyright (C) 2024-2026 Rem01Gaming (Encore Project)
# Copyright (C) 2026 kimal (LuminaTweaks)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := lumina-monitor

LOCAL_SRC_FILES := \
    main.cpp \
    BinderMonitor/BinderMonitor.cpp \
    BinderNDK/BinderNDK.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/BinderMonitor \
    $(LOCAL_PATH)/BinderNDK

LOCAL_CPPFLAGS += -std=c++20 -O2
LOCAL_CPPFLAGS += -Wall -Wextra -Wno-unused-parameter

LOCAL_LDLIBS := -llog

include $(BUILD_EXECUTABLE)
