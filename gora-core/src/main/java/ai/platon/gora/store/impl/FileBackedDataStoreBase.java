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

package ai.platon.gora.store.impl;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Properties;

import ai.platon.gora.persistency.impl.PersistentBase;
import ai.platon.gora.query.PartitionQuery;
import ai.platon.gora.query.Query;
import ai.platon.gora.query.Result;
import ai.platon.gora.store.DataStoreFactory;
import ai.platon.gora.store.FileBackedDataStore;
import ai.platon.gora.util.GoraException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementations for {@link FileBackedDataStore} methods.
 */
public abstract class FileBackedDataStoreBase<K, T extends PersistentBase>
extends DataStoreBase<K, T> implements FileBackedDataStore<K, T> {

  protected long inputSize; //input size in bytes

  protected String inputPath;
  protected String outputPath;

  protected InputStream inputStream;
  protected OutputStream outputStream;

  public static final Logger LOG = LoggerFactory.getLogger(FileBackedDataStoreBase.class);

  @Override
  public void initialize(Class<K> keyClass, Class<T> persistentClass,
          Properties properties) throws GoraException {
    super.initialize(keyClass, persistentClass, properties);
    if(properties != null) {
      if(this.inputPath == null) {
        this.inputPath = DataStoreFactory.getInputPath(properties, this);
      }
      if(this.outputPath == null) {
        this.outputPath = DataStoreFactory.getOutputPath(properties, this);
      }
    }
  }

  @Override
  public void setInputPath(String inputPath) {
    this.inputPath = inputPath;
  }

  @Override
  public void setOutputPath(String outputPath) {
    this.outputPath = outputPath;
  }

  @Override
  public String getInputPath() {
    return inputPath;
  }

  @Override
  public String getOutputPath() {
    return outputPath;
  }

  @Override
  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public void setOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  /**
   * Creates a FileSystem instance bypassing the Hadoop FileSystem cache.
   * This is needed on JDK 18+ where Subject.getSubject() throws
   * UnsupportedOperationException, breaking the FileSystem.Cache.Key
   * constructor. Avoids calling FileSystem.getFileSystemClass() which
   * triggers loadFileSystems() and may indirectly call
   * UserGroupInformation.getCurrentUser().
   */
  public static FileSystem getFileSystem(Path path, Configuration conf) throws IOException {
    URI uri = path.toUri();
    String scheme = uri.getScheme();
    if (scheme == null) {
      scheme = "file";
      uri = FileSystem.getDefaultUri(conf).resolve(uri);
    }
    String property = "fs." + scheme + ".impl";
    Class<? extends FileSystem> clazz = conf.getClass(property, null, FileSystem.class);
    if (clazz == null) {
      // Fall back to Hadoop's default: LocalFileSystem for file:/// scheme,
      // RawLocalFileSystem otherwise
      if ("file".equals(scheme)) {
        clazz = org.apache.hadoop.fs.LocalFileSystem.class;
      } else {
        clazz = org.apache.hadoop.fs.RawLocalFileSystem.class;
      }
    }
    FileSystem fs = ReflectionUtils.newInstance(clazz, conf);
    fs.initialize(uri, conf);
    return fs;
  }

  /**
   * Opens an InputStream for the input Hadoop path
   * @return an open {@link java.io.InputStream}
   * @throws IOException if there is an error obtaining the FileSystem or/from
   * the path
   */
  protected InputStream createInputStream() throws IOException {
    //TODO: if input path is a directory, use smt like MultiInputStream to
    //read all the files recursively
    Path path = new Path(inputPath);
    FileSystem fs = getFileSystem(path, getConf());
    inputSize = fs.getFileStatus(path).getLen();
    return fs.open(path);
  }

  /**
   * Opens an OutputStream for the output Hadoop path
   * @return an open {@link java.io.OutputStream}
   */
  protected OutputStream createOutputStream() {
    OutputStream conf = null;
    try{
      Path path = new Path(outputPath);
      FileSystem fs = getFileSystem(path, getConf());
      conf = fs.create(path);
    }catch(IOException ex){
      LOG.error(ex.getMessage(), ex);
    }
    return conf;
  }

  protected InputStream getOrCreateInputStream() throws IOException {
    try{
      if(inputStream == null) {
        inputStream = createInputStream();
      }
      return inputStream;
    }catch(IOException ex){
      throw new IOException(ex);
    }
  }

  protected OutputStream getOrCreateOutputStream() throws IOException {
    if(outputStream == null) {
      outputStream = createOutputStream();
    }
    return outputStream;
  }

  @Override
  public List<PartitionQuery<K, T>> getPartitions(Query<K, T> query) throws IOException {
    throw new NotImplementedException("Partitioning is not implemented for FileBackedDataStoreBase");
  }

  @Override
  public Result<K, T> executeQuery(Query<K, T> query) throws IOException {
    throw new NotImplementedException("executeQuery is not implemented for FileBackedDataStoreBase");
  }

  @Override
  public void flush() throws GoraException {
    try{
      if(outputStream != null)
        outputStream.flush();
    } catch (Exception e) {
      throw new GoraException(e);
    }
  }

  @Override
  public void createSchema() throws GoraException {
  }

  @Override
  public void deleteSchema() throws GoraException {
    throw new GoraException("delete schema is not supported for " +
            "file backed data stores");
  }

  @Override
  public boolean schemaExists() throws GoraException {
    return true;
  }

  @Override
  public void write(DataOutput out) throws IOException {
      super.write(out);
      ai.platon.gora.util.IOUtils.writeNullFieldsInfo(out, inputPath, outputPath);
      if(inputPath != null)
        Text.writeString(out, inputPath);
      if(outputPath != null)
        Text.writeString(out, outputPath);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    try{
      super.readFields(in);
      boolean[] nullFields = ai.platon.gora.util.IOUtils.readNullFieldsInfo(in);
      if(!nullFields[0])
        inputPath = Text.readString(in);
      if(!nullFields[1])
        outputPath = Text.readString(in);
    }catch(IOException ex){
      LOG.error(ex.getMessage(), ex);
    }
  }

  @Override
  public void close() {
    IOUtils.closeStream(inputStream);
    IOUtils.closeStream(outputStream);
    inputStream = null;
    outputStream = null;
  }
}
