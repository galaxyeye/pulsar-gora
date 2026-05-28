/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.gora.mongodb.store;

import ai.platon.gora.examples.generated.WebPage;
import ai.platon.gora.mongodb.GoraMongodbTestDriver;
import ai.platon.gora.mongodb.utils.BSONDecorator;
import ai.platon.gora.query.Query;
import ai.platon.gora.query.Result;
import ai.platon.gora.store.DataStoreTestBase;
import ai.platon.gora.store.impl.DataStoreBase;
import ai.platon.gora.util.GoraException;
import org.apache.avro.util.Utf8;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class TestMongoStore extends DataStoreTestBase {

    private int keySequence;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        keySequence = 1;
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public GoraMongodbTestDriver getTestDriver() {
        return (GoraMongodbTestDriver) testDriver;
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

    @Test
    public void testFromMongoList_empty() throws Exception {
        MongoStore store = new MongoStore();
        String field = "myField";
        Document emptyField = new Document(field, new ArrayList<Document>());
        Object item = store.fromMongoList(field, null,
                new BSONDecorator(emptyField), null);
        assertNotNull(item);
    }

    @Test
    public void testFromMongoMap_null() throws Exception {
        MongoStore store = new MongoStore();
        Document noField = new Document();
        String field = "myField";
        Object item = store.fromMongoMap(field, null, new BSONDecorator(noField),
                null);
        assertNotNull(item);
    }

    @Test
    public void testFromMongoMap_empty() throws Exception {
        MongoStore store = new MongoStore();
        String field = "myField";
        Document emptyField = new Document(field, new Document());
        Object item = store.fromMongoMap(field, null,
                new BSONDecorator(emptyField), null);
        assertNotNull(item);
    }

    @Test
    public void testResultProgress() throws Exception {
        Query<String, WebPage> q;

        // empty
        q = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
        assertProgress(q, 1);

        addWebPage();

        // one
        q = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
        assertProgress(q, 0, 1);

        addWebPage();

        // two
        q = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
        assertProgress(q, 0, 0.5f, 1);

        addWebPage();

        // three
        q = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
        assertProgress(q, 0, 0.33f, 0.66f, 1);
    }

    @Test
    public void testResultProgressWithLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            addWebPage();
        }
        Query<String, WebPage> q = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
        q.setLimit(2);

        assertProgress(q, 0, 0.5f, 1);
    }

    private void assertProgress(Query<String, WebPage> q, float... progress) throws Exception {
        Result<String, WebPage> r = q.execute();
        int i = 0;
        do {
            assertEquals(progress[i++], r.getProgress(), 0.01f);
        } while (r.next());
        r.close();
    }

    private void addWebPage() throws GoraException {
        String key = String.valueOf(keySequence++);
        WebPage p1 = webPageStore.newPersistent();
        p1.setUrl(new Utf8(key));
        p1.setHeaders(Collections.singletonMap((CharSequence) "header", (CharSequence) "value"));
        webPageStore.put(key, p1);
    }
}
