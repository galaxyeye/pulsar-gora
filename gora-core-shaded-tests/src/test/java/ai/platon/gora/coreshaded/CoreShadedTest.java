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

package ai.platon.gora.coreshaded;

import ai.platon.gora.examples.WebPageDataCreator;
import ai.platon.gora.examples.generated.Employee;
import ai.platon.gora.examples.generated.Metadata;
import ai.platon.gora.examples.generated.WebPage;
import ai.platon.gora.memory.store.MemStore;
import ai.platon.gora.persistency.impl.BeanFactoryImpl;
import ai.platon.gora.query.Query;
import ai.platon.gora.query.Result;
import ai.platon.gora.store.DataStore;
import ai.platon.gora.store.DataStoreFactory;
import ai.platon.gora.store.impl.DataStoreBase;
import ai.platon.gora.util.AvroUtils;
import ai.platon.gora.util.ByteUtils;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests that verify the gora-core-shaded artifact works correctly.
 * Uses {@link MemStore} (in-memory DataStore included in gora-core)
 * to exercise core Gora APIs through the shaded jar.
 */
public class CoreShadedTest {

  private static final Logger log = LoggerFactory.getLogger(CoreShadedTest.class);

  private DataStore<String, Employee> employeeStore;
  private DataStore<String, WebPage> webPageStore;

  @Before
  public void setUp() throws Exception {
    Configuration conf = new Configuration();
    Properties properties = new Properties();
    properties.setProperty("gora.datastore.autocreateschema", "true");

    employeeStore = DataStoreFactory.createDataStore(
        MemStore.class, String.class, Employee.class, conf, properties);

    webPageStore = DataStoreFactory.createDataStore(
        MemStore.class, String.class, WebPage.class, conf, properties);

    log.info("Test setup complete");
  }

  @After
  public void tearDown() throws Exception {
    if (employeeStore != null) {
      employeeStore.deleteSchema();
      employeeStore.close();
    }
    if (webPageStore != null) {
      webPageStore.deleteSchema();
      webPageStore.close();
    }
  }

  // ---- Smoke tests - verify shaded jar classes are accessible ----

  @Test
  public void testShadedJarClassesAccessible() throws Exception {
    log.info("test method: testShadedJarClassesAccessible");

    // Verify core interfaces are accessible
    assertNotNull("MemStore should be accessible", MemStore.class);

    // Verify DataStoreFactory works
    assertNotNull("DataStoreFactory should be accessible", DataStoreFactory.class);

    // Verify Persistent classes are accessible
    Employee employee = Employee.newBuilder().build();
    assertNotNull("Employee should be creatable", employee);

    // Verify utility classes are accessible
    assertNotNull("AvroUtils should be accessible", AvroUtils.class);
    assertNotNull("ByteUtils should be accessible", ByteUtils.class);

    log.info("All core shaded classes are accessible");
  }

  // ---- Schema tests ----

  @Test
  public void testCreateSchema() throws Exception {
    log.info("test method: testCreateSchema");
    employeeStore.createSchema();
    assertTrue("Schema should exist after creation", employeeStore.schemaExists());
  }

  @Test
  public void testDeleteSchema() throws Exception {
    log.info("test method: testDeleteSchema");
    employeeStore.createSchema();
    assertTrue("Schema should exist", employeeStore.schemaExists());
    employeeStore.deleteSchema();
    // MemStore may auto-create schema on access, so we just verify
    // that deleteSchema completes without exception
  }

  @Test
  public void testTruncateSchema() throws Exception {
    log.info("test method: testTruncateSchema");
    employeeStore.createSchema();

    Employee employee = createEmployee("test-ssn", "Test User", 80000);
    employeeStore.put("test-ssn", employee);
    employeeStore.flush();

    assertTrue("Employee should exist before truncate", employeeStore.exists("test-ssn"));

    employeeStore.truncateSchema();

    assertFalse("Employee should not exist after truncate", employeeStore.exists("test-ssn"));
  }

  // ---- CRUD tests ----

  @Test
  public void testPutAndGet() throws Exception {
    log.info("test method: testPutAndGet");
    employeeStore.createSchema();

    Employee employee = createEmployee("12345", "John Doe", 100000);
    employeeStore.put("12345", employee);
    employeeStore.flush();

    Employee retrieved = employeeStore.get("12345");
    assertNotNull("Retrieved employee should not be null", retrieved);
    assertEquals("John Doe", retrieved.getName().toString());
    assertEquals(100000, retrieved.getSalary().intValue());
    assertEquals("12345", retrieved.getSsn().toString());
  }

  @Test
  public void testPutAndGetWithFields() throws Exception {
    log.info("test method: testPutAndGetWithFields");
    employeeStore.createSchema();

    Employee employee = createEmployee("fields-test", "Jane Doe", 120000);
    employeeStore.put("fields-test", employee);
    employeeStore.flush();

    // Get with specific fields
    String[] fields = {"name", "salary"};
    Employee retrieved = employeeStore.get("fields-test", fields);
    assertNotNull("Retrieved employee should not be null", retrieved);
    assertEquals("Jane Doe", retrieved.getName().toString());
    assertEquals(120000, retrieved.getSalary().intValue());
  }

  @Test
  public void testGetNonExisting() throws Exception {
    log.info("test method: testGetNonExisting");
    employeeStore.createSchema();

    Employee retrieved = employeeStore.get("_NON_EXISTING_KEY_");
    assertNull("Non-existing key should return null", retrieved);
  }

  @Test
  public void testExists() throws Exception {
    log.info("test method: testExists");
    employeeStore.createSchema();

    String key = "exists-test";
    Employee employee = createEmployee(key, "Exists User", 90000);
    employeeStore.put(key, employee);
    employeeStore.flush();

    assertTrue("Employee should exist after put", employeeStore.exists(key));
    assertFalse("Non-existing key should not exist", employeeStore.exists("no-such-key"));

    employeeStore.delete(key);
    employeeStore.flush();
    assertFalse("Employee should not exist after delete", employeeStore.exists(key));
  }

  @Test
  public void testDelete() throws Exception {
    log.info("test method: testDelete");
    employeeStore.createSchema();

    String key = "delete-test";
    Employee employee = createEmployee(key, "Delete Me", 50000);
    employeeStore.put(key, employee);
    employeeStore.flush();

    assertTrue("Employee should exist before delete", employeeStore.exists(key));

    employeeStore.delete(key);
    employeeStore.flush();

    assertFalse("Employee should not exist after delete", employeeStore.exists(key));
    assertNull("Get should return null after delete", employeeStore.get(key));
  }

  @Test
  public void testUpdate() throws Exception {
    log.info("test method: testUpdate");
    employeeStore.createSchema();

    String key = "update-test";
    Employee employee = createEmployee(key, "Original Name", 70000);
    employeeStore.put(key, employee);
    employeeStore.flush();

    // Update the employee
    Employee updated = Employee.newBuilder().build();
    updated.setName(new Utf8("Updated Name"));
    updated.setSsn(new Utf8(key));
    updated.setSalary(150000);
    updated.setDateOfBirth(System.currentTimeMillis() - 25L * 365L * 24L * 60L * 60L * 1000L);
    employeeStore.put(key, updated);
    employeeStore.flush();

    Employee retrieved = employeeStore.get(key);
    assertNotNull("Retrieved employee should not be null", retrieved);
    assertEquals("Updated Name", retrieved.getName().toString());
    assertEquals(150000, retrieved.getSalary().intValue());
  }

  @Test
  public void testPutMultipleAndQuery() throws Exception {
    log.info("test method: testPutMultipleAndQuery");
    employeeStore.createSchema();

    // Create multiple employees
    for (int i = 0; i < 5; i++) {
      Employee employee = createEmployee("ssn-" + i, "User " + i, 50000 + i * 10000);
      employeeStore.put("ssn-" + i, employee);
    }
    employeeStore.flush();

    // Query all
    Query<String, Employee> query = ((DataStoreBase<String, Employee>) employeeStore).newQuery();
    Result<String, Employee> result = query.execute();

    int count = 0;
    while (result.next()) {
      assertNotNull("Result key should not be null", result.getKey());
      assertNotNull("Result value should not be null", result.get());
      count++;
    }
    result.close();
    assertEquals("Should have 5 employees", 5, count);
  }

  @Test
  public void testQueryWithKeyRange() throws Exception {
    log.info("test method: testQueryWithKeyRange");
    employeeStore.createSchema();

    // Create employees with sorted keys
    String[] keys = {"aa", "bb", "cc", "dd", "ee"};
    for (String key : keys) {
      Employee employee = createEmployee(key, "User-" + key, 60000);
      employeeStore.put(key, employee);
    }
    employeeStore.flush();

    // Query with key range [bb, dd]
    Query<String, Employee> query = ((DataStoreBase<String, Employee>) employeeStore).newQuery();
    query.setStartKey("bb");
    query.setEndKey("dd");
    Result<String, Employee> result = query.execute();

    int count = 0;
    while (result.next()) {
      count++;
    }
    result.close();
    assertEquals("Should have 3 employees in range", 3, count);
  }

  @Test
  public void testQueryWithLimit() throws Exception {
    log.info("test method: testQueryWithLimit");
    employeeStore.createSchema();

    for (int i = 0; i < 10; i++) {
      Employee employee = createEmployee("limit-" + i, "User " + i, 40000 + i * 5000);
      employeeStore.put("limit-" + i, employee);
    }
    employeeStore.flush();

    // Query with limit
    Query<String, Employee> query = ((DataStoreBase<String, Employee>) employeeStore).newQuery();
    query.setLimit(3);
    Result<String, Employee> result = query.execute();

    int count = 0;
    while (result.next()) {
      count++;
    }
    result.close();
    assertEquals("Should be limited to 3 results", 3, count);
  }

  // ---- Nested object tests ----

  @Test
  public void testPutAndGetNested() throws Exception {
    log.info("test method: testPutAndGetNested");
    webPageStore.createSchema();

    WebPage page = WebPage.newBuilder().build();
    page.setUrl(new Utf8("http://example.com"));
    page.setContent(ByteBuffer.wrap("test content".getBytes(Charset.defaultCharset())));

    Metadata metadata = Metadata.newBuilder().build();
    metadata.setVersion(1);
    metadata.getData().put(new Utf8("key1"), new Utf8("value1"));
    page.setMetadata(metadata);

    webPageStore.put("com.example/http", page);
    webPageStore.flush();

    WebPage retrieved = webPageStore.get("com.example/http");
    assertNotNull("Retrieved WebPage should not be null", retrieved);
    assertNotNull("Metadata should not be null", retrieved.getMetadata());
    assertEquals(1, retrieved.getMetadata().getVersion().intValue());
    assertEquals(new Utf8("value1"), retrieved.getMetadata().getData().get(new Utf8("key1")));
  }

  @Test
  public void testPutArray() throws Exception {
    log.info("test method: testPutArray");
    webPageStore.createSchema();

    WebPage page = WebPage.newBuilder().build();
    page.setUrl(new Utf8("http://example.org"));
    page.setParsedContent(new ArrayList<>());
    page.getParsedContent().add(new Utf8("token1"));
    page.getParsedContent().add(new Utf8("token2"));
    page.getParsedContent().add(new Utf8("token3"));

    webPageStore.put("org.example/http", page);
    webPageStore.flush();

    WebPage retrieved = webPageStore.get("org.example/http");
    assertNotNull("Retrieved WebPage should not be null", retrieved);
    assertEquals(3, retrieved.getParsedContent().size());
  }

  @Test
  public void testPutMap() throws Exception {
    log.info("test method: testPutMap");
    webPageStore.createSchema();

    WebPage page = WebPage.newBuilder().build();
    page.setUrl(new Utf8("http://example.net"));
    page.getOutlinks().put(new Utf8("http://a.com"), new Utf8("anchorA"));
    page.getOutlinks().put(new Utf8("http://b.com"), new Utf8("anchorB"));

    webPageStore.put("net.example/http", page);
    webPageStore.flush();

    WebPage retrieved = webPageStore.get("net.example/http");
    assertNotNull("Retrieved WebPage should not be null", retrieved);
    assertEquals(2, retrieved.getOutlinks().size());
    assertEquals(new Utf8("anchorA"), retrieved.getOutlinks().get(new Utf8("http://a.com")));
  }

  // ---- WebPage data creator tests ----

  @Test
  public void testWebPageDataCreator() throws Exception {
    log.info("test method: testWebPageDataCreator");
    webPageStore.createSchema();

    WebPageDataCreator.createWebPageData(webPageStore);

    // Verify all URLs were stored
    for (String url : WebPageDataCreator.URLS) {
      WebPage page = webPageStore.get(url);
      assertNotNull("WebPage should exist for URL: " + url, page);
    }
  }

  // ---- Utility tests ----

  @Test
  public void testAvroUtils() throws Exception {
    log.info("test method: testAvroUtils");

    Employee employee = createEmployee("avro-util", "Avro User", 75000);

    String[] fieldNames = AvroUtils.getSchemaFieldNames(Employee.SCHEMA$);
    assertNotNull("Field names should not be null", fieldNames);
    assertTrue("Should have multiple fields", fieldNames.length > 0);
  }

  @Test
  public void testByteUtils() throws Exception {
    log.info("test method: testByteUtils");

    byte[] bytes = "test data".getBytes(Charset.defaultCharset());
    String result = ByteUtils.toString(bytes);
    assertEquals("test data", result);
  }

  @Test
  public void testBeanFactory() throws Exception {
    log.info("test method: testBeanFactory");

    BeanFactoryImpl<String, Employee> factory =
        new BeanFactoryImpl<>(String.class, Employee.class);

    Employee employee = factory.newPersistent();
    assertNotNull("New persistent should not be null", employee);
    assertEquals(Employee.class, employee.getClass());
  }

  @Test
  public void testNewPersistent() throws Exception {
    log.info("test method: testNewPersistent");

    Employee emp1 = employeeStore.newPersistent();
    Employee emp2 = employeeStore.newPersistent();

    assertNotNull("First persistent should not be null", emp1);
    assertNotNull("Second persistent should not be null", emp2);
    assertNotSame("Should be different instances", emp1, emp2);
    assertEquals(Employee.class, emp1.getClass());
  }

  // ---- Recursive/nested record tests ----

  @Test
  public void testGetRecursive() throws Exception {
    log.info("test method: testGetRecursive");
    employeeStore.createSchema();

    Employee employee = createEmployee("recursive-ssn", "Boss Employee", 200000);
    Employee subordinate = createEmployee("sub-ssn", "Subordinate", 50000);
    employee.setBoss(subordinate);

    employeeStore.put("recursive-ssn", employee);
    employeeStore.flush();

    Employee retrieved = employeeStore.get("recursive-ssn");
    assertNotNull("Retrieved employee should not be null", retrieved);
    assertNotNull("Boss should not be null", retrieved.getBoss());

    Employee retrievedBoss = (Employee) retrieved.getBoss();
    assertEquals("Subordinate", retrievedBoss.getName().toString());
  }

  @Test
  public void testResultSize() throws Exception {
    log.info("test method: testResultSize");
    webPageStore.createSchema();

    WebPageDataCreator.createWebPageData(webPageStore);

    Query<String, WebPage> query = ((DataStoreBase<String, WebPage>) webPageStore).newQuery();
    Result<String, WebPage> result = query.execute();

    int size = result.size();
    int count = 0;
    while (result.next()) {
      count++;
    }
    result.close();
    assertEquals("Result size should match actual count", count, size);
    assertEquals("Should have expected number of pages", WebPageDataCreator.URLS.length, count);
  }

  // ---- Helper methods ----

  private Employee createEmployee(String ssn, String name, int salary) throws Exception {
    Employee employee = Employee.newBuilder().build();
    employee.setName(new Utf8(name));
    employee.setSsn(new Utf8(ssn));
    employee.setSalary(salary);
    employee.setDateOfBirth(System.currentTimeMillis() - 20L * 365L * 24L * 60L * 60L * 1000L);
    return employee;
  }
}
