/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.search.stats;

import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.metrics.MeanMetric;
import org.elasticsearch.common.metrics.CounterMapMetric;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.TextFieldMapper;
import org.elasticsearch.index.shard.SearchOperationListener;
import org.elasticsearch.search.internal.ReaderContext;
import org.elasticsearch.search.internal.SearchContext;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;

public final class ShardSearchStats implements SearchOperationListener {

    private final StatsHolder totalStats = new StatsHolder();
    private final CounterMetric openContexts = new CounterMetric();
    private volatile Map<String, StatsHolder> groupsStats = emptyMap();

    /**
     * Returns the stats, including group specific stats. If the groups are null/0 length, then nothing
     * is returned for them. If they are set, then only groups provided will be returned, or
     * {@code _all} for all groups.
     */
    public SearchStats stats(String... groups) {
        SearchStats.Stats total = totalStats.stats();
        Map<String, SearchStats.Stats> groupsSt = null;
        if (CollectionUtils.isEmpty(groups) == false) {
            groupsSt = new HashMap<>(groupsStats.size());
            if (groups.length == 1 && groups[0].equals("_all")) {
                for (Map.Entry<String, StatsHolder> entry : groupsStats.entrySet()) {
                    groupsSt.put(entry.getKey(), entry.getValue().stats());
                }
            } else {
                for (Map.Entry<String, StatsHolder> entry : groupsStats.entrySet()) {
                    if (Regex.simpleMatch(groups, entry.getKey())) {
                        groupsSt.put(entry.getKey(), entry.getValue().stats());
                    }
                }
            }
        }
        return new SearchStats(total, openContexts.count(), groupsSt);
    }

    @Override
    public void onPreQueryPhase(SearchContext searchContext) {
        computeStats(searchContext, statsHolder -> {
            if (searchContext.hasOnlySuggest()) {
                statsHolder.suggestCurrent.inc();
            } else {
                statsHolder.queryCurrent.inc();
            }
        });
    }

    @Override
    public void onFailedQueryPhase(SearchContext searchContext) {
        computeStats(searchContext, statsHolder -> {
            if (searchContext.hasOnlySuggest()) {
                statsHolder.suggestCurrent.dec();
                assert statsHolder.suggestCurrent.count() >= 0;
            } else {
                statsHolder.queryCurrent.dec();
                assert statsHolder.queryCurrent.count() >= 0;
            }
        });
    }

    @Override
    public void onQueryPhase(SearchContext searchContext, long tookInNanos) {
        computeStats(searchContext, statsHolder -> {
            if (searchContext.hasOnlySuggest()) {
                statsHolder.suggestMetric.inc(tookInNanos);
                statsHolder.suggestCurrent.dec();
                assert statsHolder.suggestCurrent.count() >= 0;
            } else {
                statsHolder.queryMetric.inc(tookInNanos);
                statsHolder.queryCurrent.dec();
                // if fields with type text are not in the maps, then, insert all of them
                if (statsHolder.indexPrefixMapMetric.size() == 0) {
                    statsHolder.indexPrefixMapMetric.put(getTextFields(searchContext));
                }
                if (statsHolder.nonIndexPrefixMapMetric.size() == 0) {
                    statsHolder.nonIndexPrefixMapMetric.put(getTextFields(searchContext));
                }
                updateIndexPrefixMetrics(searchContext, statsHolder);
                assert statsHolder.queryCurrent.count() >= 0;
            }
        });
    }

    @Override
    public void onPreFetchPhase(SearchContext searchContext) {
        computeStats(searchContext, statsHolder -> statsHolder.fetchCurrent.inc());
    }

    @Override
    public void onFailedFetchPhase(SearchContext searchContext) {
        computeStats(searchContext, statsHolder -> statsHolder.fetchCurrent.dec());
    }

    @Override
    public void onFetchPhase(SearchContext searchContext, long tookInNanos) {
        computeStats(searchContext, statsHolder -> {
            statsHolder.fetchMetric.inc(tookInNanos);
            statsHolder.fetchCurrent.dec();
            assert statsHolder.fetchCurrent.count() >= 0;
        });
    }

    private void computeStats(SearchContext searchContext, Consumer<StatsHolder> consumer) {
        consumer.accept(totalStats);
        if (searchContext.groupStats() != null) {
            for (String group : searchContext.groupStats()) {
                consumer.accept(groupStats(group));
            }
        }
    }

    private StatsHolder groupStats(String group) {
        StatsHolder stats = groupsStats.get(group);
        if (stats == null) {
            synchronized (this) {
                stats = groupsStats.get(group);
                if (stats == null) {
                    stats = new StatsHolder();
                    groupsStats = MapBuilder.newMapBuilder(groupsStats).put(group, stats).immutableMap();
                }
            }
        }
        return stats;
    }

    private List<String> getTextFields(SearchContext searchContext) {
        List<String> textFields = new ArrayList<>();
        for (MappedFieldType fieldType: searchContext.getQueryShardContext().getFieldTypes()) {
            if (fieldType instanceof TextFieldMapper.TextFieldType) {
                textFields.add(fieldType.name());
            }
        }
        return textFields;
    }

    private void updateIndexPrefixMetrics(SearchContext searchContext, StatsHolder statsHolder) {
        /* String description of a query contains substrings of form "field:value" in addition to the type of query info.
         * The query processed by index prefix have string "._index_prefix" appended to their field.
         * Thus, we need to count the number of occurrence of "._index_prefix:" to get index prefix metric.
         */

        List<String> fields = QueryDescriptionParser.getFieldsFromQuery(searchContext.query().toString());
        for (String field: fields) {
            if (field.length() > 14) {
                String suffix = field.substring(field.length()-14);
                if (suffix.equals("._index_prefix")) {
                    statsHolder.indexPrefixMetric.inc();
                    statsHolder.indexPrefixMapMetric.inc(field.substring(0, field.length()-14));
                }
                else if (suffix.equals("._index_phrase")) {
                    statsHolder.nonIndexPrefixMetric.inc();
                    statsHolder.nonIndexPrefixMapMetric.inc(field.substring(0, field.length()-14));
                }
                else if (searchContext.getQueryShardContext().getFieldType(field) instanceof TextFieldMapper.TextFieldType) {
                    statsHolder.nonIndexPrefixMetric.inc();
                    statsHolder.nonIndexPrefixMapMetric.inc(field);
                }
            }
            else if (searchContext.getQueryShardContext().getFieldType(field) instanceof TextFieldMapper.TextFieldType) {
                statsHolder.nonIndexPrefixMetric.inc();
                statsHolder.nonIndexPrefixMapMetric.inc(field);
            }
        }
    }

    @Override
    public void onNewReaderContext(ReaderContext readerContext) {
        openContexts.inc();
    }

    @Override
    public void onFreeReaderContext(ReaderContext readerContext) {
        openContexts.dec();
    }

    @Override
    public void onNewScrollContext(ReaderContext readerContext) {
        totalStats.scrollCurrent.inc();
    }

    @Override
    public void onFreeScrollContext(ReaderContext readerContext) {
        totalStats.scrollCurrent.dec();
        assert totalStats.scrollCurrent.count() >= 0;
        totalStats.scrollMetric.inc(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - readerContext.getStartTimeInNano()));
    }

    static final class QueryDescriptionParser {
        static final String SpecialCharacters = "[+\\-&|!()\\[\\]{}^\"~\\*? ]";
        static final Pattern pattern = Pattern.compile(SpecialCharacters);

        public static List<String> getFieldsFromQuery(String query) {
            List<String> fields = new ArrayList<>();
            Matcher matcher = pattern.matcher(query);
            int last_special_char_index = -1, j = -1;
            for (int i=0; i<query.length(); i++) {
                if (query.charAt(i) == ':' && (i>0 && query.charAt(i-1) != '\\')) {
                    while (j < i) {
                        if (!(j > 0 && query.charAt(j-1) == '\\')) {
                            last_special_char_index = j;
                        }
                        if (matcher.find()) {
                            j = matcher.start();
                        }
                        else {
                            break;
                        }
                    }
                    fields.add(query.substring(last_special_char_index+1, i));
                }
            }
            return fields;
        }
    }

    static final class StatsHolder {
        final MeanMetric queryMetric = new MeanMetric();
        final MeanMetric fetchMetric = new MeanMetric();
        /* We store scroll statistics in microseconds because with nanoseconds we run the risk of overflowing the total stats if there are
         * many scrolls. For example, on a system with 2^24 scrolls that have been executed, each executing for 2^10 seconds, then using
         * nanoseconds would require a numeric representation that can represent at least 2^24 * 2^10 * 10^9 > 2^24 * 2^10 * 2^29 = 2^63
         * which exceeds the largest value that can be represented by a long. By using microseconds, we enable capturing one-thousand
         * times as many scrolls (i.e., billions of scrolls which at one per second would take 32 years to occur), or scrolls that execute
         * for one-thousand times as long (i.e., scrolls that execute for almost twelve days on average).
         */
        final MeanMetric scrollMetric = new MeanMetric();
        final MeanMetric suggestMetric = new MeanMetric();
        final CounterMetric queryCurrent = new CounterMetric();
        final CounterMetric fetchCurrent = new CounterMetric();
        final CounterMetric scrollCurrent = new CounterMetric();
        final CounterMetric suggestCurrent = new CounterMetric();
        final CounterMetric indexPrefixMetric = new CounterMetric();
        final CounterMetric nonIndexPrefixMetric = new CounterMetric();
        final CounterMapMetric<String> indexPrefixMapMetric = new CounterMapMetric<>();
        final CounterMapMetric<String> nonIndexPrefixMapMetric = new CounterMapMetric<>();


        SearchStats.Stats stats() {
            return new SearchStats.Stats(
                    queryMetric.count(), TimeUnit.NANOSECONDS.toMillis(queryMetric.sum()), queryCurrent.count(),
                    fetchMetric.count(), TimeUnit.NANOSECONDS.toMillis(fetchMetric.sum()), fetchCurrent.count(),
                    scrollMetric.count(), TimeUnit.MICROSECONDS.toMillis(scrollMetric.sum()), scrollCurrent.count(),
                    suggestMetric.count(), TimeUnit.NANOSECONDS.toMillis(suggestMetric.sum()), suggestCurrent.count(),
                    indexPrefixMetric.count(), nonIndexPrefixMetric.count(), indexPrefixMapMetric.count(), nonIndexPrefixMapMetric.count()
            );
        }
    }
}
