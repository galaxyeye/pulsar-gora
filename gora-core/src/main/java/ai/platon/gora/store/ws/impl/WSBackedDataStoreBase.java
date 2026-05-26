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

package ai.platon.gora.store.ws.impl;

import java.io.IOException;
import java.util.Properties;

import ai.platon.gora.persistency.Persistent;
import ai.platon.gora.query.Query;
import ai.platon.gora.query.Result;
import ai.platon.gora.store.WebServiceBackedDataStore;
import ai.platon.gora.util.GoraException;
import ai.platon.gora.util.OperationNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementations for {@link WebServiceBackedDataStore} methods.
 */
public abstract class WSBackedDataStoreBase<K, T extends Persistent>
  extends WSDataStoreBase<K, T> implements WebServiceBackedDataStore<K, T> {

  private static final Logger LOG = LoggerFactory.getLogger(WSBackedDataStoreBase.class);

  @Override
  /**
   * Initializes a web service backed data store
   * @throws IOException
   */
  public void initialize(Class<K> keyClass, Class<T> persistentClass,
      Properties properties) throws GoraException {
    super.initialize(keyClass, persistentClass, properties);
  }

  /**
   * Executes a normal Query reading the whole data. #execute() calls this function
   * for non-PartitionQuery's.
   */
  public abstract Result<K,T> executeQuery(Query<K,T> query)
    throws IOException;

  @Override
  /**
   * Flushes objects into the data store
   */
  public void flush() throws GoraException {
  }

  @Override
  /**
   * Creates schema into the data store
   */
  public void createSchema() throws GoraException {
  }

  @Override
  /**
   * Deletes schema from the data store
   */
  public void deleteSchema() throws GoraException {
    throw new OperationNotSupportedException("delete schema is not supported for " +
      "file backed data stores");
  }

  @Override
  /**
   * Verifies if a schema exists
   */
  public boolean schemaExists() throws GoraException {
    return true;
  }

  @Override
  /**
   * Writes an object into the data
   */
  public void write(Object out) throws Exception {
    super.write(out);
  }

  @Override
  /**
   * Reads fields from an object
   */
  public void readFields(Object in) throws Exception {
    super.readFields(in);
  }

  @Override
  /**
   * Closes the data store
   */
  public void close() {
  }
}
