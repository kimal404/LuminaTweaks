    // Tambahkan di properti class GameDeckService:
    private var deckManualProfile: String? = null
    private var savedAutoProfileBeforeDeck: Boolean? = null

    // Di dalam onStartCommand:
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeConfig = GameDeckPrefs.load(this)
        when (intent?.action) {
            ACTION_SHOW_NOW -> {
                manualOverride = true
                startForegroundCompat()
                startTelemetryLoop()
                lastMetricsMs = 0L
                return START_STICKY
            }
            ACTION_HIDE_NOW -> {
                manualOverride = false
                if (!activeConfig.enabled) {
                    restoreManagerProfileIfNeeded()
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTelemetryLoop()
                return START_STICKY
            }
            ACTION_STOP -> {
                GameDeckPrefs.save(this, activeConfig.copy(enabled = false))
                manualOverride = false
                restoreManagerProfileIfNeeded()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SYNC -> {
                manualOverride = null
            }
        }

        if (!activeConfig.enabled && manualOverride != true) {
            restoreManagerProfileIfNeeded()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        startTelemetryLoop()
        return START_STICKY
    }

    // Di dalam onDestroy:
    override fun onDestroy() {
        restoreManagerProfileIfNeeded()
        telemetryJob?.cancel()
        transitionJob?.cancel()
        profileJob?.cancel()
        detachOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    // Di dalam startTelemetryLoop() saat menyusun uiState.value:
    val resolvedProfile = deckManualProfile ?: if (afterMs < manualProfileGraceUntilMs) {
        uiState.value.profile
    } else {
        snapshot.profile
    }

    // Di dalam attachOverlay() -> setContent { LuminaTheme { ... } }:
    GameDeckRoot(
        state = uiState,
        isWindowExpanded = isWindowExpandedState.value,
        onExpand = { setExpanded(true) },
        onCollapse = { setExpanded(false) },
        onProfileSelected = ::selectProfile,
        onRefreshRateSelected = ::selectRefreshRate,
        onToggleDnd = ::toggleDnd,
        onQuickAppClick = ::launchFreeformApp,
        onOpenManager = ::openManager
    )

    // Ganti toggleAutoProfile() + selectProfile() dengan dua fungsi ini:
    private fun selectProfile(name: String) {
        profileJob?.cancel()
        if (savedAutoProfileBeforeDeck == null) {
            savedAutoProfileBeforeDeck = uiState.value.autoProfile
        }
        deckManualProfile = name
        manualProfileGraceUntilMs = System.currentTimeMillis() + 5_000L
        uiState.value = uiState.value.copy(profile = name)

        profileJob = serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { DaemonManager.setAutoProfile(false) }
                DaemonManager.setProfile(name)
            }
            if (!ok) {
                Toast.makeText(
                    this@GameDeckService,
                    localizedText("Daemon Lumina tidak aktif", "Lumina daemon is offline"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun restoreManagerProfileIfNeeded() {
        if (deckManualProfile == null) return
        val restoreAuto = savedAutoProfileBeforeDeck ?: true
        deckManualProfile = null
        savedAutoProfileBeforeDeck = null
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { DaemonManager.setAutoProfile(restoreAuto) }
        }
    }
