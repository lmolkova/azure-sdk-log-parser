package com.azure.sdklogparser.util;

import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.telemetry.Telemetry;


public class RunInfoTelemetryInitializer implements TelemetryInitializer {

    private final RunInfo runInfo;
    public RunInfoTelemetryInitializer(RunInfo runInfo) {
        this.runInfo = runInfo;
    }

    @Override
    public void initialize(Telemetry telemetry) {
        telemetry.getContext().getCloud().setRole(runInfo.getRunName());
        telemetry.getContext().getCloud().setRoleInstance(runInfo.getUniqueId());
        runInfo.nextRecord(telemetry);
    }
}
