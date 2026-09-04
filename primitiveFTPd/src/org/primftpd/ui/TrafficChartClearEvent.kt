package org.primftpd.ui

/**
 * Posted after the persisted traffic-chart history has been cleared from the cleaner screen.
 * [org.primftpd.ui.viewmodel.NetworkViewModel] listens for this event and resets its in-memory chart history.
 */
class TrafficChartClearEvent
