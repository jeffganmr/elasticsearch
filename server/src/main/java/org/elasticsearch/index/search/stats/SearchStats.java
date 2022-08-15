/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.search.stats;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SearchStats implements Writeable, ToXContentFragment {

    public static class Stats implements Writeable, ToXContentFragment {

        private long queryCount;
        private long queryTimeInMillis;
        private long queryCurrent;

        private long fetchCount;
        private long fetchTimeInMillis;
        private long fetchCurrent;

        private long scrollCount;
        private long scrollTimeInMillis;
        private long scrollCurrent;

        private long suggestCount;
        private long suggestTimeInMillis;
        private long suggestCurrent;

        private long indexPrefixCount;
        private long nonIndexPrefixCount;

        private Map<String, Long> indexPrefixMap;
        private Map<String, Long> nonIndexPrefixMap;
        private Map<String, Float> indexPrefixPercentageMap;

        private Stats() {
            // for internal use, initializes all counts to 0
            indexPrefixMap = new HashMap<>();
            nonIndexPrefixMap = new HashMap<>();
            indexPrefixPercentageMap = new HashMap<>();
        }

        public Stats(
                long queryCount, long queryTimeInMillis, long queryCurrent,
                long fetchCount, long fetchTimeInMillis, long fetchCurrent,
                long scrollCount, long scrollTimeInMillis, long scrollCurrent,
                long suggestCount, long suggestTimeInMillis, long suggestCurrent,
                long indexPrefixCount, long nonIndexPrefixCount, Map<String, Long> indexPrefixMap,
                Map<String, Long> nonIndexPrefixMap
        ) {
            this.queryCount = queryCount;
            this.queryTimeInMillis = queryTimeInMillis;
            this.queryCurrent = queryCurrent;

            this.fetchCount = fetchCount;
            this.fetchTimeInMillis = fetchTimeInMillis;
            this.fetchCurrent = fetchCurrent;

            this.scrollCount = scrollCount;
            this.scrollTimeInMillis = scrollTimeInMillis;
            this.scrollCurrent = scrollCurrent;

            this.suggestCount = suggestCount;
            this.suggestTimeInMillis = suggestTimeInMillis;
            this.suggestCurrent = suggestCurrent;

            this.indexPrefixCount = indexPrefixCount;
            this.nonIndexPrefixCount = nonIndexPrefixCount;

            this.indexPrefixMap = indexPrefixMap;
            this.nonIndexPrefixMap = nonIndexPrefixMap;
            if (this.indexPrefixPercentageMap == null) {
                this.indexPrefixPercentageMap = new HashMap<>();
            }
            updateIndexPrefixPercentageMap();
        }

        private Stats(StreamInput in) throws IOException {
            queryCount = in.readVLong();
            queryTimeInMillis = in.readVLong();
            queryCurrent = in.readVLong();

            fetchCount = in.readVLong();
            fetchTimeInMillis = in.readVLong();
            fetchCurrent = in.readVLong();

            scrollCount = in.readVLong();
            scrollTimeInMillis = in.readVLong();
            scrollCurrent = in.readVLong();

            suggestCount = in.readVLong();
            suggestTimeInMillis = in.readVLong();
            suggestCurrent = in.readVLong();

            indexPrefixCount = in.readVLong();
            nonIndexPrefixCount = in.readVLong();

            indexPrefixMap = in.readMap(StreamInput::readString, StreamInput::readVLong);
            nonIndexPrefixMap = in.readMap(StreamInput::readString, StreamInput::readVLong);
            indexPrefixPercentageMap = in.readMap(StreamInput::readString, StreamInput::readFloat);
        }

        public void add(Stats stats) {
            queryCount += stats.queryCount;
            queryTimeInMillis += stats.queryTimeInMillis;
            queryCurrent += stats.queryCurrent;

            fetchCount += stats.fetchCount;
            fetchTimeInMillis += stats.fetchTimeInMillis;
            fetchCurrent += stats.fetchCurrent;

            scrollCount += stats.scrollCount;
            scrollTimeInMillis += stats.scrollTimeInMillis;
            scrollCurrent += stats.scrollCurrent;

            suggestCount += stats.suggestCount;
            suggestTimeInMillis += stats.suggestTimeInMillis;
            suggestCurrent += stats.suggestCurrent;

            indexPrefixCount += stats.indexPrefixCount;
            nonIndexPrefixCount += stats.nonIndexPrefixCount;

            addIndexPrefixMaps(stats);
            updateIndexPrefixPercentageMap();
        }

        public void addForClosingShard(Stats stats) {
            queryCount += stats.queryCount;
            queryTimeInMillis += stats.queryTimeInMillis;

            fetchCount += stats.fetchCount;
            fetchTimeInMillis += stats.fetchTimeInMillis;

            scrollCount += stats.scrollCount;
            scrollTimeInMillis += stats.scrollTimeInMillis;
            // need consider the count of the shard's current scroll
            scrollCount += stats.scrollCurrent;

            suggestCount += stats.suggestCount;
            suggestTimeInMillis += stats.suggestTimeInMillis;

            indexPrefixCount += stats.indexPrefixCount;
            nonIndexPrefixCount += stats.nonIndexPrefixCount;

            addIndexPrefixMaps(stats);
            updateIndexPrefixPercentageMap();
        }

        // Utility function to add indexPrefixMap, nonIndexPrefixMap
        private void addIndexPrefixMaps(Stats stats) {
            for (String field: stats.indexPrefixMap.keySet()) {
                indexPrefixMap.put(field, indexPrefixMap.getOrDefault(field, 0L) + stats.indexPrefixMap.get(field));
            }
            for (String field: stats.nonIndexPrefixMap.keySet()) {
                nonIndexPrefixMap.put(field, nonIndexPrefixMap.getOrDefault(field, 0L) + stats.nonIndexPrefixMap.get(field));
            }
        }

        // Utility function to update percentage of index prefixes
        private void updateIndexPrefixPercentageMap() {
            for (String field: indexPrefixMap.keySet()) {
                long fieldIndexPrefixCount = indexPrefixMap.get(field);
                long fieldNonIndexPrefixCount = nonIndexPrefixMap.getOrDefault(field, 0L);
                long total = fieldIndexPrefixCount + fieldNonIndexPrefixCount;
                if (total == 0) {
                    indexPrefixPercentageMap.put(field, 0f);
                }
                else {
                    indexPrefixPercentageMap.put(field, fieldIndexPrefixCount*100f/total);
                }
            }
        }

        public long getQueryCount() {
            return queryCount;
        }

        public TimeValue getQueryTime() {
            return new TimeValue(queryTimeInMillis);
        }

        public long getQueryTimeInMillis() {
            return queryTimeInMillis;
        }

        public long getQueryCurrent() {
            return queryCurrent;
        }

        public long getFetchCount() {
            return fetchCount;
        }

        public TimeValue getFetchTime() {
            return new TimeValue(fetchTimeInMillis);
        }

        public long getFetchTimeInMillis() {
            return fetchTimeInMillis;
        }

        public long getFetchCurrent() {
            return fetchCurrent;
        }

        public long getScrollCount() {
            return scrollCount;
        }

        public TimeValue getScrollTime() {
            return new TimeValue(scrollTimeInMillis);
        }

        public long getScrollTimeInMillis() {
            return scrollTimeInMillis;
        }

        public long getScrollCurrent() {
            return scrollCurrent;
        }

        public long getSuggestCount() {
            return suggestCount;
        }

        public long getSuggestTimeInMillis() {
            return suggestTimeInMillis;
        }

        public TimeValue getSuggestTime() {
            return new TimeValue(suggestTimeInMillis);
        }

        public long getSuggestCurrent() {
            return suggestCurrent;
        }


        public long getIndexPrefixCount() {
            return indexPrefixCount;
        }

        public long getNonIndexPrefixCount() {
            return  nonIndexPrefixCount;
        }

        public Map<String, Long> getIndexPrefixMap() {
            return  indexPrefixMap;
        }

        public Map<String, Long> getNonIndexPrefixMap() {
            return nonIndexPrefixMap;
        }

        public Map<String, Float> getIndexPrefixPercentageMap() {
            return indexPrefixPercentageMap;
        }

        public static Stats readStats(StreamInput in) throws IOException {
            return new Stats(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVLong(queryCount);
            out.writeVLong(queryTimeInMillis);
            out.writeVLong(queryCurrent);

            out.writeVLong(fetchCount);
            out.writeVLong(fetchTimeInMillis);
            out.writeVLong(fetchCurrent);

            out.writeVLong(scrollCount);
            out.writeVLong(scrollTimeInMillis);
            out.writeVLong(scrollCurrent);

            out.writeVLong(suggestCount);
            out.writeVLong(suggestTimeInMillis);
            out.writeVLong(suggestCurrent);

            out.writeVLong(indexPrefixCount);
            out.writeVLong(nonIndexPrefixCount);

            out.writeMap(indexPrefixMap, StreamOutput::writeString, StreamOutput::writeVLong);
            out.writeMap(nonIndexPrefixMap, StreamOutput::writeString, StreamOutput::writeVLong);
            out.writeMap(indexPrefixPercentageMap, StreamOutput::writeString, StreamOutput::writeFloat);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field(Fields.QUERY_TOTAL, queryCount);
            builder.humanReadableField(Fields.QUERY_TIME_IN_MILLIS, Fields.QUERY_TIME, getQueryTime());
            builder.field(Fields.QUERY_CURRENT, queryCurrent);

            builder.field(Fields.FETCH_TOTAL, fetchCount);
            builder.humanReadableField(Fields.FETCH_TIME_IN_MILLIS, Fields.FETCH_TIME, getFetchTime());
            builder.field(Fields.FETCH_CURRENT, fetchCurrent);

            builder.field(Fields.SCROLL_TOTAL, scrollCount);
            builder.humanReadableField(Fields.SCROLL_TIME_IN_MILLIS, Fields.SCROLL_TIME, getScrollTime());
            builder.field(Fields.SCROLL_CURRENT, scrollCurrent);

            builder.field(Fields.SUGGEST_TOTAL, suggestCount);
            builder.humanReadableField(Fields.SUGGEST_TIME_IN_MILLIS, Fields.SUGGEST_TIME, getSuggestTime());
            builder.field(Fields.SUGGEST_CURRENT, suggestCurrent);

            builder.field(Fields.INDEX_PREFIX_TOTAL, indexPrefixCount);
            builder.field(Fields.NON_INDEX_PREFIX_TOTAL, nonIndexPrefixCount);

            builder.field(Fields.INDEX_PREFIX_COUNT, indexPrefixMap);
            builder.field(Fields.NON_INDEX_PREFIX_COUNT, nonIndexPrefixMap);
            builder.field(Fields.INDEX_PREFIX_PERCENTAGE, indexPrefixPercentageMap);

            return builder;
        }
    }

    private final Stats totalStats;
    private long openContexts;

    @Nullable
    private Map<String, Stats> groupStats;

    public SearchStats() {
        totalStats = new Stats();
    }

    public SearchStats(Stats totalStats, long openContexts, @Nullable Map<String, Stats> groupStats) {
        this.totalStats = totalStats;
        this.openContexts = openContexts;
        this.groupStats = groupStats;
    }

    public SearchStats(StreamInput in) throws IOException {
        totalStats = Stats.readStats(in);
        openContexts = in.readVLong();
        if (in.readBoolean()) {
            groupStats = in.readMap(StreamInput::readString, Stats::readStats);
        }
    }

    public void add(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        addTotals(searchStats);
        openContexts += searchStats.openContexts;
        if (searchStats.groupStats != null && !searchStats.groupStats.isEmpty()) {
            if (groupStats == null) {
                groupStats = new HashMap<>(searchStats.groupStats.size());
            }
            for (Map.Entry<String, Stats> entry : searchStats.groupStats.entrySet()) {
                groupStats.putIfAbsent(entry.getKey(), new Stats());
                groupStats.get(entry.getKey()).add(entry.getValue());
            }
        }
    }

    public void addTotals(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        totalStats.add(searchStats.totalStats);
    }

    public void addTotalsForClosingShard(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        totalStats.addForClosingShard(searchStats.totalStats);
    }

    public Stats getTotal() {
        return this.totalStats;
    }

    public long getOpenContexts() {
        return this.openContexts;
    }

    @Nullable
    public Map<String, Stats> getGroupStats() {
        return this.groupStats != null ? Collections.unmodifiableMap(this.groupStats) : null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject(Fields.SEARCH);
        builder.field(Fields.OPEN_CONTEXTS, openContexts);
        totalStats.toXContent(builder, params);
        if (groupStats != null && !groupStats.isEmpty()) {
            builder.startObject(Fields.GROUPS);
            for (Map.Entry<String, Stats> entry : groupStats.entrySet()) {
                builder.startObject(entry.getKey());
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    @Override
    public String toString() {
        return Strings.toString(this, true, true);
    }

    static final class Fields {
        static final String SEARCH = "search";
        static final String OPEN_CONTEXTS = "open_contexts";
        static final String GROUPS = "groups";
        static final String QUERY_TOTAL = "query_total";
        static final String QUERY_TIME = "query_time";
        static final String QUERY_TIME_IN_MILLIS = "query_time_in_millis";
        static final String QUERY_CURRENT = "query_current";
        static final String FETCH_TOTAL = "fetch_total";
        static final String FETCH_TIME = "fetch_time";
        static final String FETCH_TIME_IN_MILLIS = "fetch_time_in_millis";
        static final String FETCH_CURRENT = "fetch_current";
        static final String SCROLL_TOTAL = "scroll_total";
        static final String SCROLL_TIME = "scroll_time";
        static final String SCROLL_TIME_IN_MILLIS = "scroll_time_in_millis";
        static final String SCROLL_CURRENT = "scroll_current";
        static final String SUGGEST_TOTAL = "suggest_total";
        static final String SUGGEST_TIME = "suggest_time";
        static final String SUGGEST_TIME_IN_MILLIS = "suggest_time_in_millis";
        static final String SUGGEST_CURRENT = "suggest_current";
        static final String INDEX_PREFIX_TOTAL = "index_prefix_total";
        static final String NON_INDEX_PREFIX_TOTAL = "non_index_prefix_total";
        static final String INDEX_PREFIX_COUNT = "index_prefix_count";
        static final String NON_INDEX_PREFIX_COUNT = "non_index_prefix_count";
        static final String INDEX_PREFIX_PERCENTAGE = "index_prefix_percentage";
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        totalStats.writeTo(out);
        out.writeVLong(openContexts);
        if (groupStats == null || groupStats.isEmpty()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeMap(groupStats, StreamOutput::writeString, (stream, stats) -> stats.writeTo(stream));
        }
    }
}
