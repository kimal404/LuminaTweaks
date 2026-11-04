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
#include <sstream>
#include <string>

#ifndef LOG_TAG
#define LOG_TAG "LuminaMonitor"
#endif

namespace lumina::log {
    inline void format_impl(std::ostringstream &oss, const std::string &fmt) {
        oss << fmt;
    }

    template <typename T, typename... Args>
    inline void format_impl(std::ostringstream &oss, const std::string &fmt, const T &first, const Args &...rest) {
        size_t pos = fmt.find("{}");
        if (pos != std::string::npos) {
            oss << fmt.substr(0, pos) << first;
            format_impl(oss, fmt.substr(pos + 2), rest...);
        } else {
            oss << fmt;
        }
    }

    template <typename... Args>
    inline std::string format(const std::string &fmt, const Args &...args) {
        std::ostringstream oss;
        format_impl(oss, fmt, args...);
        return oss.str();
    }
} // namespace lumina::log

#define LOGI_TAG(tag, fmt, ...) do { \
    std::string _msg = ::lumina::log::format(fmt __VA_OPT__(,) __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", _msg.c_str()); \
    std::printf("[INFO][%s] %s\n", tag, _msg.c_str()); \
} while (0)

#define LOGE_TAG(tag, fmt, ...) do { \
    std::string _msg = ::lumina::log::format(fmt __VA_OPT__(,) __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_ERROR, tag, "%s", _msg.c_str()); \
    std::fprintf(stderr, "[ERROR][%s] %s\n", tag, _msg.c_str()); \
} while (0)

#define LOGW_TAG(tag, fmt, ...) do { \
    std::string _msg = ::lumina::log::format(fmt __VA_OPT__(,) __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_WARN, tag, "%s", _msg.c_str()); \
    std::fprintf(stderr, "[WARN][%s] %s\n", tag, _msg.c_str()); \
} while (0)

#define LOGD_TAG(tag, fmt, ...) do { \
    std::string _msg = ::lumina::log::format(fmt __VA_OPT__(,) __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_DEBUG, tag, "%s", _msg.c_str()); \
} while (0)
