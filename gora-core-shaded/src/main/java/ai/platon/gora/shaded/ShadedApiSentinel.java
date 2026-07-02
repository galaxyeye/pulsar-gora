/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.platon.gora.shaded;

import ai.platon.gora.GoraVersionAnnotation;
import ai.platon.gora.avro.query.AvroQuery;
import ai.platon.gora.avro.query.AvroResult;
import ai.platon.gora.avro.query.DataFileAvroResult;
import ai.platon.gora.avro.store.AvroStore;
import ai.platon.gora.avro.store.DataFileAvroStore;
import ai.platon.gora.filter.Filter;
import ai.platon.gora.filter.FilterList;
import ai.platon.gora.filter.FilterOp;
import ai.platon.gora.filter.MapFieldValueFilter;
import ai.platon.gora.filter.SingleFieldValueFilter;
import ai.platon.gora.flink.PersistentTypeInfo;
import ai.platon.gora.flink.PersistentTypeInfoFactory;
import ai.platon.gora.flink.PersistentTypeSerializer;
import ai.platon.gora.mapreduce.PersistentDeserializer;
import ai.platon.gora.mapreduce.PersistentSerialization;
import ai.platon.gora.mapreduce.PersistentSerializer;
import ai.platon.gora.memory.store.MemStore;
import ai.platon.gora.persistency.BeanFactory;
import ai.platon.gora.persistency.Dirtyable;
import ai.platon.gora.persistency.Persistent;
import ai.platon.gora.persistency.Tombstone;
import ai.platon.gora.persistency.Tombstones;
import ai.platon.gora.persistency.impl.BeanFactoryImpl;
import ai.platon.gora.persistency.impl.DirtyCollectionWrapper;
import ai.platon.gora.persistency.impl.DirtyListWrapper;
import ai.platon.gora.persistency.impl.DirtyMapWrapper;
import ai.platon.gora.persistency.impl.DirtySetWrapper;
import ai.platon.gora.persistency.impl.PersistentBase;
import ai.platon.gora.persistency.ws.impl.BeanFactoryWSImpl;
import ai.platon.gora.persistency.ws.impl.PersistentWSBase;
import ai.platon.gora.query.PartitionQuery;
import ai.platon.gora.query.Query;
import ai.platon.gora.query.Result;
import ai.platon.gora.query.impl.PartitionQueryImpl;
import ai.platon.gora.query.impl.QueryBase;
import ai.platon.gora.query.impl.ResultBase;
import ai.platon.gora.query.ws.impl.PartitionWSQueryImpl;
import ai.platon.gora.query.ws.impl.QueryWSBase;
import ai.platon.gora.query.ws.impl.ResultWSBase;
import ai.platon.gora.store.DataStore;
import ai.platon.gora.store.DataStoreFactory;
import ai.platon.gora.store.DataStoreMetadataFactory;
import ai.platon.gora.store.FileBackedDataStore;
import ai.platon.gora.store.WebServiceBackedDataStore;
import ai.platon.gora.store.impl.DataStoreBase;
import ai.platon.gora.store.impl.DataStoreMetadataAnalyzer;
import ai.platon.gora.store.impl.FileBackedDataStoreBase;
import ai.platon.gora.store.ws.impl.WSBackedDataStoreBase;
import ai.platon.gora.store.ws.impl.WSDataStoreBase;
import ai.platon.gora.store.ws.impl.WSDataStoreFactory;
import ai.platon.gora.util.AvroUtils;
import ai.platon.gora.util.ByteUtils;
import ai.platon.gora.util.ClassLoadingUtils;
import ai.platon.gora.util.GoraException;
import ai.platon.gora.util.IOUtils;
import ai.platon.gora.util.NodeWalker;
import ai.platon.gora.util.Null;
import ai.platon.gora.util.OperationNotSupportedException;
import ai.platon.gora.util.ReflectionUtils;
import ai.platon.gora.util.StringUtils;
import ai.platon.gora.util.TimingUtil;
import ai.platon.gora.util.VersionInfo;
import ai.platon.gora.util.WritableUtils;

/**
 * Sentinel class whose sole purpose is to ensure the Maven Shade Plugin's
 * {@code minimizeJar} includes all the Gora Core API classes that consumers
 * of {@code gora-core-shaded} need at runtime.
 *
 * <p>The shade plugin's bytecode minimizer starts from this class and follows
 * all references transitively. By referencing every key Gora type here, we
 * guarantee the shaded jar contains the complete public API surface while
 * still stripping truly unreachable code from dependencies.</p>
 *
 * <p>This class is never instantiated — it exists purely as a bytecode anchor.</p>
 */
@SuppressWarnings("all")
public final class ShadedApiSentinel {

    private ShadedApiSentinel() {
        throw new UnsupportedOperationException("sentinel class — do not instantiate");
    }

    // ---- Static initializer forces bytecode references to all key types ----

    private static final Class<?>[] API_CLASSES = {
        // store
        DataStore.class,
        DataStoreFactory.class,
        DataStoreMetadataFactory.class,
        DataStoreBase.class,
        DataStoreMetadataAnalyzer.class,
        FileBackedDataStore.class,
        FileBackedDataStoreBase.class,
        WebServiceBackedDataStore.class,
        WSBackedDataStoreBase.class,
        WSDataStoreBase.class,
        WSDataStoreFactory.class,

        // memory store
        MemStore.class,

        // avro store
        AvroStore.class,
        DataFileAvroStore.class,

        // persistency
        Persistent.class,
        PersistentBase.class,
        Dirtyable.class,
        Tombstone.class,
        Tombstones.class,
        BeanFactory.class,
        BeanFactoryImpl.class,
        BeanFactoryWSImpl.class,
        PersistentWSBase.class,

        // dirty wrappers
        DirtyCollectionWrapper.class,
        DirtyListWrapper.class,
        DirtyMapWrapper.class,
        DirtySetWrapper.class,

        // query
        Query.class,
        QueryBase.class,
        QueryWSBase.class,
        Result.class,
        ResultBase.class,
        ResultWSBase.class,
        PartitionQuery.class,
        PartitionQueryImpl.class,
        PartitionWSQueryImpl.class,

        // avro query
        AvroQuery.class,
        AvroResult.class,
        DataFileAvroResult.class,

        // filter
        Filter.class,
        FilterOp.class,
        FilterList.class,
        SingleFieldValueFilter.class,
        MapFieldValueFilter.class,

        // flink
        PersistentTypeInfo.class,
        PersistentTypeInfoFactory.class,
        PersistentTypeSerializer.class,

        // mapreduce
        PersistentDeserializer.class,
        PersistentSerializer.class,
        PersistentSerialization.class,

        // util
        AvroUtils.class,
        ByteUtils.class,
        ClassLoadingUtils.class,
        GoraException.class,
        IOUtils.class,
        NodeWalker.class,
        Null.class,
        OperationNotSupportedException.class,
        ReflectionUtils.class,
        StringUtils.class,
        TimingUtil.class,
        VersionInfo.class,
        WritableUtils.class,

        // version
        GoraVersionAnnotation.class,
    };

    /**
     * Registers all API types with the sentinel. The shade plugin's minimizer
     * traces from this call site to find reachable classes.
     */
    public static Class<?>[] getApiClasses() {
        return API_CLASSES;
    }
}
