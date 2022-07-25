/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.internal;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Objects;

public final class ShardSearchContextId implements Writeable {
    private final String sessionId;
    private final long id;

    private String distributedTraceId;

    public ShardSearchContextId(String sessionId, long id) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.id = id;
    }

    public ShardSearchContextId(String sessionId, long id, String distributedTraceId) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.id = id;
        this.distributedTraceId = distributedTraceId;
    }

    public ShardSearchContextId(StreamInput in) throws IOException {
        this.id = in.readLong();
        if (in.getVersion().onOrAfter(Version.V_7_7_0)) {
            this.sessionId = in.readString();
        } else {
            this.sessionId = "";
        }
        this.distributedTraceId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(id);
        if (out.getVersion().onOrAfter(Version.V_7_7_0)) {
            out.writeString(sessionId);
        }
        out.writeOptionalString(distributedTraceId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShardSearchContextId other = (ShardSearchContextId) o;
        return id == other.id && sessionId.equals(other.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, id);
    }

    @Override
    public String toString() {
        return "[" + sessionId + "][" + id + "]";
    }


    public String getDistributedTraceId() {
        return distributedTraceId;
    }

    public void setDistributedTraceId(String distributedTraceId) {
        this.distributedTraceId = distributedTraceId;
    }

    private boolean isDistributedTraceIdSame(String other) {
        if (this.distributedTraceId == null && other == null) {
            return true;
        }
        if (this.distributedTraceId != null && other != null) {
            return this.distributedTraceId.equals(other);
        }
        return false;
    }
}
