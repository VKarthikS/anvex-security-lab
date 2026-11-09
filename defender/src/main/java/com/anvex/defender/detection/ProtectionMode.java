package com.anvex.defender.detection;

public enum ProtectionMode {
    ON,
    OFF;

    public boolean isOn() {
        return this == ON;
    }
}
