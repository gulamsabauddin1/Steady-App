package com.steady.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    private var lastBlockedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val openedPackage = event?.packageName?.toString() ?: return
        val session = FocusStore(this).loadSession()
        if (session == null) {
            lastBlockedPackage = null
            return
        }

        if (openedPackage !in session.blockedPackages) {
            lastBlockedPackage = null
            return
        }

        if (openedPackage != lastBlockedPackage) {
            lastBlockedPackage = openedPackage
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit
}
