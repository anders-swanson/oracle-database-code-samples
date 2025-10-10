package com.example.tracing.jdbc;

import oracle.jdbc.TraceEventListener;

public class OracleTraceEventListener implements TraceEventListener {
    private TraceEventListener traceEventListener;

    public TraceEventListener getTraceEventListener() {
        return traceEventListener;
    }

    public void setTraceEventListener(TraceEventListener traceEventListener) {
        this.traceEventListener = traceEventListener;
    }

    @Override
    public Object roundTrip(Sequence sequence, TraceContext traceContext, Object o) {
        return this.traceEventListener.roundTrip(sequence, traceContext, o);
    }

    @Override
    public Object onExecutionEventReceived(JdbcExecutionEvent event, Object userContext, Object... params) {
        return this.traceEventListener.onExecutionEventReceived(event, userContext, params);
    }

    @Override
    public boolean isDesiredEvent(JdbcExecutionEvent event) {
        return this.traceEventListener.isDesiredEvent(event);
    }
}
