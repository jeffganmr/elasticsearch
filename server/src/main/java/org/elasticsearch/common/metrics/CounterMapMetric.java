/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.metrics;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class CounterMapMetric<K> {
    private final Map<K, LongAdder> counterMap = new HashMap<>();

    public synchronized void put(Collection<K> keys) {
        for (K key: keys) {
            if (!counterMap.containsKey(key)) {
                counterMap.put(key, new LongAdder());
            }
        }
    }

    public int size() {
        return counterMap.size();
    }

    public void inc(K key) {
        counterMap.get(key).increment();
    }

    public void inc(CounterMapMetric<K> counterMapMetric) {
        for (Map.Entry entry: counterMap.entrySet()) {
            K key = (K)entry.getKey();
            if (counterMapMetric.counterMap.containsKey(key)) {
                inc(key, counterMapMetric.counterMap.get(key).sum());
            }
        }
    }

    public void inc(K key, long n) {
        counterMap.get(key).add(n);
    }

    public void dec(K key) {
        counterMap.get(key).decrement();
    }

    public void dec(K key, long n) {
        counterMap.get(key).add(-n);
    }

    public Map<K, Long> count() {
        Map<K, Long> res = new HashMap<>();
        for (Map.Entry entry: counterMap.entrySet()) {
            res.put((K)entry.getKey(), ((LongAdder)entry.getValue()).sum());
        }
        return res;
    }
}
