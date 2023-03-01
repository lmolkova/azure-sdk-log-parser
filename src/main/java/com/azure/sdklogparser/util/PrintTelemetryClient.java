package com.azure.sdklogparser.util;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;

public class PrintTelemetryClient extends TelemetryClient {
    @Override
    public void trackTrace(TraceTelemetry telemetry) {
        System.out.printf("time:%s level:%s message:%s%n", telemetry.getTimestamp(),
                telemetry.getSeverityLevel(),
                telemetry.getMessage());

        if (!telemetry.getProperties().isEmpty()) {
            telemetry.getProperties().forEach((k, v) -> System.out.printf("\t%s:%s%n", k, v));
        }
    }

    @Override
    public void track(Telemetry telemetry) {
        if (telemetry instanceof TraceTelemetry) {
            trackTrace((TraceTelemetry) telemetry);
            return;
        }

        System.out.printf("time:%s telemetry:%s%n", telemetry.getTimestamp(), telemetry.getClass());

        if (!telemetry.getProperties().isEmpty()) {
            telemetry.getProperties().forEach((k, v) -> System.out.printf("\t%s:%s%n", k, v));
        }

    }
}
