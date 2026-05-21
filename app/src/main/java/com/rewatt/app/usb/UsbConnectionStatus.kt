package com.rewatt.app.usb

enum class UsbConnectionStatus {
    Disconnected,
    Scanning,
    PermissionRequired,
    PermissionDenied,
    Connected,
    DriverMissing,
    Error,
}

