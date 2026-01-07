package com.orbit;

import com.limelight.nvstream.NvConnectionListener;

public class NvConnectionListenerAdapter implements NvConnectionListener {
    @Override
    public void stageStarting(String stage) {
    }

    @Override
    public void stageComplete(String stage) {
    }

    @Override
    public void stageFailed(String stage, int portFlags, int errorCode) {
    }

    @Override
    public void connectionStarted() {
    }

    @Override
    public void connectionTerminated(int errorCode) {
    }

    @Override
    public void connectionStatusUpdate(int connectionStatus) {
    }

    @Override
    public void displayMessage(String message) {
    }

    @Override
    public void displayTransientMessage(String message) {
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
    }
}
