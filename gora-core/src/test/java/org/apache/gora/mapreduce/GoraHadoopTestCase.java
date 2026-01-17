package org.apache.gora.mapreduce;

import com.globalmentor.apache.hadoop.fs.BareLocalFileSystem;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MiniMRCluster;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

public abstract class GoraHadoopTestCase {
  public static final int LOCAL_MR = 1;
  public static final int CLUSTER_MR = 2;
  public static final int LOCAL_FS = 4;
  public static final int DFS_FS = 8;
  private boolean localMR;
  private boolean localFS;
  private int taskTrackers;
  private int dataNodes;
  private MiniDFSCluster dfsCluster = null;
  private MiniMRCluster mrCluster = null;
  private FileSystem fileSystem = null;

  public GoraHadoopTestCase(int mrMode, int fsMode, int taskTrackers, int dataNodes) throws IOException {
    if (mrMode != 1 && mrMode != 2) {
      throw new IllegalArgumentException("Invalid MapRed mode, must be LOCAL_MR or CLUSTER_MR");
    } else if (fsMode != 4 && fsMode != 8) {
      throw new IllegalArgumentException("Invalid FileSystem mode, must be LOCAL_FS or DFS_FS");
    } else if (taskTrackers < 1) {
      throw new IllegalArgumentException("Invalid taskTrackers value, must be greater than 0");
    } else if (dataNodes < 1) {
      throw new IllegalArgumentException("Invalid dataNodes value, must be greater than 0");
    } else {
      this.localMR = mrMode == 1;
      this.localFS = fsMode == 4;
      this.taskTrackers = taskTrackers;
      this.dataNodes = dataNodes;
    }
  }

  public boolean isLocalMR() {
    return this.localMR;
  }

  public boolean isLocalFS() {
    return this.localFS;
  }
  private JobConf job;

  public JobConf getJob() {
    return job;
  }

  @Before
  public void setUp() throws Exception {
    if (this.localFS) {

      job = createJobConf();
      job.setClass("fs.file.impl", BareLocalFileSystem.class, FileSystem.class);

      this.fileSystem = FileSystem.getLocal(job);
    } else {
      this.dfsCluster = (new MiniDFSCluster.Builder(new JobConf())).numDataNodes(this.dataNodes).build();
      this.fileSystem = this.dfsCluster.getFileSystem();
    }

    if (!this.localMR) {
      this.mrCluster = new MiniMRCluster(this.taskTrackers, this.fileSystem.getUri().toString(), 1);
    }
  }

  @After
  public void tearDown() throws Exception {
    try {
      if (this.mrCluster != null) {
        this.mrCluster.shutdown();
      }
    } catch (Exception ex) {
      System.out.println(ex);
    }

    try {
      if (this.dfsCluster != null) {
        this.dfsCluster.shutdown();
      }
    } catch (Exception ex) {
      System.out.println(ex);
    }

  }

  protected FileSystem getFileSystem() {
    return this.fileSystem;
  }

  protected JobConf createJobConf() {
    if (this.localMR) {
      JobConf conf = new JobConf();
      conf.set("mapreduce.framework.name", "local");
      return conf;
    } else {
      return this.mrCluster.createJobConf();
    }
  }
}
