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

package ai.platon.gora.query.impl;

import ai.platon.gora.mock.persistency.MockPersistent;
import ai.platon.gora.mock.query.MockQuery;
import ai.platon.gora.mock.store.MockDataStore;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for {@link PartitionQueryImpl}
 */
public class TestPartitionQueryImpl {

  private MockDataStore dataStore = MockDataStore.get();

  @Test
  public void testReadWrite() throws Exception {

    MockQuery baseQuery = new MockQuery(dataStore);
    baseQuery.setStartKey("start");
    baseQuery.setLimit(42);

    PartitionQueryImpl<String, MockPersistent>
      query = new PartitionQueryImpl<>(baseQuery);

    // Serialize to byte buffer
    DataOutputBuffer out = new DataOutputBuffer();
    query.write(out);

    // Deserialize from byte buffer
    DataInputBuffer in = new DataInputBuffer();
    in.reset(out.getData(), out.getLength());

    PartitionQueryImpl<String, MockPersistent> deserializedQuery =
        (PartitionQueryImpl<String, MockPersistent>)
            ReflectionUtils.newInstance(query.getClass(), null);
    deserializedQuery.readFields(in);

    // Verify round-trip
    assertEquals(query.getStartKey(), deserializedQuery.getStartKey());
    assertEquals(query.getEndKey(), deserializedQuery.getEndKey());
    assertEquals(query.getLimit(), deserializedQuery.getLimit());
    assertEquals(query.getBaseQuery().getStartKey(),
        deserializedQuery.getBaseQuery().getStartKey());
    assertEquals(query.getBaseQuery().getLimit(),
        deserializedQuery.getBaseQuery().getLimit());
  }

}
