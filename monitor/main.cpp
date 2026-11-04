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

#include "BinderMonitor/BinderMonitor.hpp"
#include "Log.hpp"

#include <csignal>
#include <cstdlib>
#include <string>
#include <unistd.h>

#define TAG "LuminaMonitor"
#define DEFAULT_RESOLVER_PATH "/data/adb/modules/luminatweaks/bin/binder_resolver.apk"

static void setupSignalHandler() {
    struct sigaction sa{};
    sa.sa_handler = [](int signum) {
        LOGI_TAG(TAG, "Menerima sinyal %d, menghentikan lumina-monitor...", signum);
        std::_Exit(0);
    };
    sigaction(SIGTERM, &sa, nullptr);
    sigaction(SIGINT, &sa, nullptr);
}

int main(int argc, char *argv[]) {
    setupSignalHandler();

    std::string resolverPath = DEFAULT_RESOLVER_PATH;
    if (argc > 1) {
        resolverPath = argv[1];
    }

    LOGI_TAG(TAG, "Starting lumina-monitor (PID: %d)...", getpid());
    LOGI_TAG(TAG, "Resolver target path: %s", resolverPath.c_str());

    if (access(resolverPath.c_str(), R_OK) != 0) {
        LOGE_TAG(TAG, "File resolver tidak ditemukan di: %s", resolverPath.c_str());
        return 1;
    }

    auto &monitor = BinderMonitor::get();

    ProcessObserverCallbacks callbacks{};
    callbacks.onForegroundActivitiesChanged = [&](int32_t pid, int32_t uid, bool foreground) {
        if (!foreground) return;

        std::string pkg = monitor.getPackageNameForUid(uid);
        if (pkg.empty()) pkg = "unknown";

        LOGI_TAG(TAG, "[EVENT] Foreground -> PKG: %s | PID: %d | UID: %d", pkg.c_str(), pid, uid);
    };

    callbacks.onProcessDied = [](int32_t pid, int32_t uid) {
        LOGD_TAG(TAG, "[EVENT] Process Died -> PID: %d | UID: %d", pid, uid);
    };

    monitor.setProcessObserverCallbacks(std::move(callbacks));

    if (!monitor.initialize(resolverPath)) {
        LOGE_TAG(TAG, "Gagal menginisialisasi BinderMonitor.");
        return 1;
    }

    LOGI_TAG(TAG, "Lumina-monitor siap. Menunggu event binder...");
    monitor.joinThreadPool();

    return 0;
}
