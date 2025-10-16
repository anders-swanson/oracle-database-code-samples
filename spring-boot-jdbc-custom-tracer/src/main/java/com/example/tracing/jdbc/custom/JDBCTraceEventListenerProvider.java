package com.example.tracing.jdbc.custom;

import io.opentelemetry.api.trace.Tracer;
import oracle.jdbc.TraceEventListener;
import oracle.jdbc.spi.TraceEventListenerProvider;

import java.util.Map;

public class JDBCTraceEventListenerProvider implements TraceEventListenerProvider {
    private static final String PROVIDER_NAME = "custom-jdbc-trace-event-listener-provider";
    private static final JDBCTraceEventListener TEL = new JDBCTraceEventListener();

    @Override
    public TraceEventListener getTraceEventListener(Map<Parameter, CharSequence> map) {
        return TEL;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    public static void setTracer(Tracer tracer) {
        TEL.setTracer(tracer);
    }

    public static void setTracingProperties(TracingProperties tracingProperties) {
        TEL.setTracingProperties(tracingProperties);
    }
}
