/*
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

#include "Log.hpp"

#include <string>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>
#include <mutex>
#include <cstring>

#define DEFAULT_LUMINAD_SOCKET "/dev/socket/luminad.sock"

class IpcClient {
public:
    static IpcClient &get() {
        static IpcClient instance;
        return instance;
    }

    void init(const std::string &socketPath = DEFAULT_LUMINAD_SOCKET) {
        std::lock_guard<std::mutex> lock(mMutex);
        mSocketPath = socketPath;
        connectLocked();
    }

    bool sendEvent(const std::string &jsonPayload) {
        std::lock_guard<std::mutex> lock(mMutex);

        if (mSocketFd < 0) {
            if (!connectLocked()) {
                return false;
            }
        }

        std::string message = jsonPayload + "\n";
        ssize_t sent = send(mSocketFd, message.c_str(), message.length(), MSG_NOSIGNAL);

        if (sent < 0) {
            LOGW_TAG("IpcClient", "Gagal mengirim data ke daemon (errno: {}), mereset koneksi...", errno);
            close(mSocketFd);
            mSocketFd = -1;
            return false;
        }

        return true;
    }

    ~IpcClient() {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mSocketFd >= 0) {
            close(mSocketFd);
            mSocketFd = -1;
        }
    }

private:
    IpcClient() : mSocketFd(-1), mSocketPath(DEFAULT_LUMINAD_SOCKET) {}
    IpcClient(const IpcClient &) = delete;
    IpcClient &operator=(const IpcClient &) = delete;

    bool connectLocked() {
        if (mSocketFd >= 0) {
            close(mSocketFd);
            mSocketFd = -1;
        }

        mSocketFd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (mSocketFd < 0) {
            LOGE_TAG("IpcClient", "Gagal membuat AF_UNIX socket");
            return false;
        }

        struct sockaddr_un addr{};
        addr.sun_family = AF_UNIX;
        std::strncpy(addr.sun_path, mSocketPath.c_str(), sizeof(addr.sun_path) - 1);

        if (connect(mSocketFd, reinterpret_cast<struct sockaddr *>(&addr), sizeof(addr)) < 0) {
            LOGD_TAG("IpcClient", "Daemon belum aktif di {}", mSocketPath);
            close(mSocketFd);
            mSocketFd = -1;
            return false;
        }

        LOGI_TAG("IpcClient", "Terhubung ke daemon socket: {}", mSocketPath);
        return true;
    }

    int mSocketFd;
    std::string mSocketPath;
    std::mutex mMutex;
};
