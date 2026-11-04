/*
 * Copyright (C) 2024-2026 Rem01Gaming (Encore Project)
 * Copyright (C) 2026 kimal (LuminaTweaks)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <android/log.h>
#include <cstdio>

#ifndef LOG_TAG
#define LOG_TAG "LuminaMonitor"
#endif

#define LOGI_TAG(tag, fmt, ...) do { \
    __android_log_print(ANDROID_LOG_INFO, tag, fmt, ##__VA_ARGS__); \
    std::printf("[INFO][%s] " fmt "\n", tag, ##__VA_ARGS__); \
} while (0)

#define LOGE_TAG(tag, fmt, ...) do { \
    __android_log_print(ANDROID_LOG_ERROR, tag, fmt, ##__VA_ARGS__); \
    std::fprintf(stderr, "[ERROR][%s] " fmt "\n", tag, ##__VA_ARGS__); \
} while (0)

#define LOGW_TAG(tag, fmt, ...) do { \
    __android_log_print(ANDROID_LOG_WARN, tag, fmt, ##__VA_ARGS__); \
    std::fprintf(stderr, "[WARN][%s] " fmt "\n", tag, ##__VA_ARGS__); \
} while (0)

#define LOGD_TAG(tag, fmt, ...) do { \
    __android_log_print(ANDROID_LOG_DEBUG, tag, fmt, ##__VA_ARGS__); \
} while (0)
