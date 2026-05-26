/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.gora.memory.store;

import ai.platon.gora.examples.WebPageDataCreator;
import ai.platon.gora.examples.generated.WebPage;
import ai.platon.gora.persistency.BeanFactory;
import ai.platon.gora.persistency.impl.BeanFactoryImpl;
import ai.platon.gora.store.DataStore;
import ai.platon.gora.store.DataStoreTestBase;
import ai.platon.gora.util.GoraException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ai.platon.gora.examples.WebPageDataCreator.URLS;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Testing class for all standard gora-memory functionality.
 * We extend {@link DataStoreTestBase} enabling us to run the entire base test
 * suite for Gora. 
 */
public class MemStoreTest extends DataStoreTestBase {

    static {
        setTestDriver(new MemStoreTestDriver());
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testGetMissingValue() throws GoraException {
        DataStore<String, WebPage> store = new MemStore<>();
        WebPage nullWebPage = store.get("missing", new String[0]);
        assertNull(nullWebPage);
        store.close();
    }

    @Test
    @Ignore("MemStore has no concept of a schema")
    public void testTruncateSchema() throws Exception {
    }

    @Test
    @Ignore("MemStore has no concept of a schema")
    public void testDeleteSchema() throws Exception {
    }

    @Test
    @Ignore("MemStore has no concept of a schema")
    public void testSchemaExists() throws Exception {
    }

    @Test
    public void testPutGet() throws Exception {
        String key = "ai.platon.gora:http:/";
        DataStore<String, WebPage> store = new MemStore<>();
        assumeTrue(store.get(key, new String[0]) == null);
        store.put(key, WebPage.newBuilder().build());
        assertNotNull(store.get(key, new String[0]));
        store.close();
    }

    @Test
    public void testGetWithFields() throws Exception {

        DataStore<String, WebPage> store = new MemStore<>();
        BeanFactory<String, WebPage> beanFactory = new BeanFactoryImpl<>(String.class, WebPage.class);
        store.setBeanFactory(beanFactory);
        WebPageDataCreator.createWebPageData(store);
        String[] interestFields = new String[2];
        interestFields[0] = "url";
        interestFields[1] = "content";
        WebPage page = store.get(URLS[1], interestFields);
        assertNotNull(page);
        assertNotNull(page.getUrl());
        assertTrue(page.getOutlinks().size() > 0);
        assertTrue(page.getParsedContent().size() > 0);
    }
}


