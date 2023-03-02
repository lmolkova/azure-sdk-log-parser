package com.azure.sdklogparser.util;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;

/**
 * Telemetry client used for dry-runs rather than pushing data into application insights.
 */
public class ConsoleTelemetryClient extends TelemetryClient {
    private static final String PROPERTY_FORMAT = "\t%s: %s%n";
    private static final String UNKNOWN = "unknown";

    @Override
    public void trackTrace(TraceTelemetry telemetry) {
        final String timestamp = telemetry.getTimestamp() != null
                ? telemetry.getTimestamp().toString()
                : telemetry.getProperties().getOrDefault(TokenType.TIMESTAMP.getValue(), UNKNOWN);

        System.out.printf("%s (%s): %s%n", timestamp, telemetry.getSeverityLevel(),
                telemetry.getMessage());

        if (!telemetry.getProperties().isEmpty()) {
            telemetry.getProperties().forEach((k, v) -> System.out.printf(PROPERTY_FORMAT, k, v));
        }
    }

    @Override
    public void track(Telemetry telemetry) {
        if (telemetry instanceof TraceTelemetry) {
            trackTrace((TraceTelemetry) telemetry);
            return;
        }

        final String timestamp = telemetry.getTimestamp() != null
                ? telemetry.getTimestamp().toString()
                : telemetry.getProperties().getOrDefault(TokenType.TIMESTAMP.getValue(), UNKNOWN);

        System.out.printf("%s telemetry: %s%n", timestamp, telemetry.getClass());

        if (!telemetry.getProperties().isEmpty()) {
            telemetry.getProperties().forEach((k, v) -> System.out.printf(PROPERTY_FORMAT, k, v));
        }

    }
}
