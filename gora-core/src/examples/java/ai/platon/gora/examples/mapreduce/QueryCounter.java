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

package ai.platon.gora.examples.mapreduce;

import java.io.IOException;

import ai.platon.gora.mapreduce.GoraMapper;
import ai.platon.gora.persistency.Persistent;
import ai.platon.gora.query.Query;
import ai.platon.gora.store.DataStore;import ai.platon.gora.store.DataStoreFactory;
import ai.platon.gora.store.impl.DataStoreBase;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import ai.platon.gora.util.ClassLoadingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example Hadoop job to count the row of a gora {@link Query}.
 */
public class QueryCounter<K, T extends Persistent> extends Configured implements Tool {

  private static final Logger LOG = LoggerFactory.getLogger(QueryCounter.class);

  public static final String COUNTER_GROUP = "QueryCounter";
  public static final String ROWS = "ROWS";

  public QueryCounter(Configuration conf) {
    setConf(conf);
  }

  public static class QueryCounterMapper<K, T extends Persistent>
      extends GoraMapper<K, T, NullWritable, NullWritable> {

    @Override
    protected void map(K key, T value,
        Context context) throws IOException ,InterruptedException {

      context.getCounter(COUNTER_GROUP, ROWS).increment(1L);
    }
  }

  /**
   * Creates and returns the {@link Job} for submitting to Hadoop mapreduce.
   * @param query
   * @return
   * @throws IOException
   */
  public Job createJob(Query<K,T> query) throws IOException {
    Job job = Job.getInstance(getConf());

    job.setJobName("QueryCounter");
    job.setNumReduceTasks(0);
    job.setJarByClass(getClass());
    /* Mappers are initialized with GoraMapper.initMapper()*/
    GoraMapper.initMapperJob(job, query, NullWritable.class
        , NullWritable.class, QueryCounterMapper.class, true);

    job.setOutputFormatClass(NullOutputFormat.class);
    return job;
  }


  /**
   * Returns the number of results to the Query
   */
  public long countQuery(Query<K,T> query) throws Exception {
    Job job = createJob(query);
    job.waitForCompletion(true);
    assert(job.isComplete());

    return job.getCounters().findCounter(COUNTER_GROUP, ROWS).getValue();
  }

  @SuppressWarnings("unchecked")
  @Override
  public int run(String[] args) throws Exception {

    if(args.length < 2) {
      LOG.info("Usage QueryCounter <keyClass> <persistentClass> [dataStoreClass]");
      return 1;
    }

    Class<K> keyClass = (Class<K>) ClassLoadingUtils.loadClass(args[0]);
    Class<T> persistentClass = (Class<T>) ClassLoadingUtils.loadClass(args[1]);

    DataStore<K,T> dataStore;
    Configuration conf = new Configuration();

    if(args.length > 2) {
      Class<? extends DataStore<K,T>> dataStoreClass
          = (Class<? extends DataStore<K, T>>) Class.forName(args[2]);
      dataStore = DataStoreFactory.getDataStore(dataStoreClass, keyClass, persistentClass, conf);
    }
    else {
      dataStore = DataStoreFactory.getDataStore(keyClass, persistentClass, conf);
    }

    return 0;
  }


  @SuppressWarnings("rawtypes")
  public static void main(String[] args) throws Exception {
    int ret = ToolRunner.run(new QueryCounter(new Configuration()), args);
    System.exit(ret);
  }
}
