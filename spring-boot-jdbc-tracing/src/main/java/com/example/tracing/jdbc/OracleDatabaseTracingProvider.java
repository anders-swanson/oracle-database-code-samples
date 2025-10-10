package com.example.tracing.jdbc;

import oracle.jdbc.TraceEventListener;
import oracle.jdbc.spi.TraceEventListenerProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class OracleDatabaseTracingProvider implements TraceEventListenerProvider {
    private static final String NAME = "oracle-database-trace-event-listener-provider";
    private static final OracleTraceEventListener traceEventListener = new OracleTraceEventListener();

    @Override
    public TraceEventListener getTraceEventListener(Map<Parameter, CharSequence> map) {
        return traceEventListener;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Collection<? extends Parameter> getParameters() {
        return Collections.emptyList();
    }

    public static void setTraceEventListener(TraceEventListener tel) {
        traceEventListener.setTraceEventListener(tel);
    }
}
