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
package ai.platon.gora.mongodb.store;

import ai.platon.gora.mongodb.GoraMongodbTestDriver;
import ai.platon.gora.mongodb.utils.BSONDecorator;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Perform {@link TestMongoStore} tests on MongoDB 4.2.x server.
 */
public class TestMongoStore42 extends TestMongoStore {

  private static final MongoDBContainer container = new MongoDBContainer("mongo:4.2");

  static {
    setTestDriver(new GoraMongodbTestDriver(container));
  }

  @Test
  public void testFromMongoList_null() throws Exception {
    MongoStore store = new MongoStore();
    Document noField = new Document();
    String field = "myField";
    Object item = store.fromMongoList(field, null, new BSONDecorator(noField),
            null);
    assertNotNull(item);
  }
}
