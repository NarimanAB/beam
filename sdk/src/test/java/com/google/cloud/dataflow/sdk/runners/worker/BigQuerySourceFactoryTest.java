/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.runners.worker;

import static com.google.cloud.dataflow.sdk.util.CoderUtils.makeCloudEncoding;
import static com.google.cloud.dataflow.sdk.util.Structs.addString;

import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.util.BatchModeExecutionContext;
import com.google.cloud.dataflow.sdk.util.CloudObject;
import com.google.cloud.dataflow.sdk.util.common.worker.Source;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for BigQuerySourceFactory.
 */
@RunWith(JUnit4.class)
public class BigQuerySourceFactoryTest {
  void runTestCreateBigQuerySource(String project,
                                   String dataset,
                                   String table,
                                   CloudObject encoding)
      throws Exception {
    CloudObject spec = CloudObject.forClassName("BigQuerySource");
    addString(spec, "project", project);
    addString(spec, "dataset", dataset);
    addString(spec, "table", table);

    com.google.api.services.dataflow.model.Source cloudSource =
        new com.google.api.services.dataflow.model.Source();
    cloudSource.setSpec(spec);
    cloudSource.setCodec(encoding);

    Source<?> source = SourceFactory.create(PipelineOptionsFactory.create(),
                                            cloudSource,
                                            new BatchModeExecutionContext());
    Assert.assertThat(source, new IsInstanceOf(BigQuerySource.class));
    BigQuerySource bigQuerySource = (BigQuerySource) source;
    Assert.assertEquals(project, bigQuerySource.tableRef.getProjectId());
    Assert.assertEquals(dataset, bigQuerySource.tableRef.getDatasetId());
    Assert.assertEquals(table, bigQuerySource.tableRef.getTableId());
  }

  @Test
  public void testCreateBigQuerySource() throws Exception {
    runTestCreateBigQuerySource(
        "someproject", "somedataset", "sometable",
        makeCloudEncoding("TableRowJsonCoder"));
  }

  @Test
  public void testCreateBigQuerySourceCoderIgnored() throws Exception {
    // BigQuery sources do not need a coder because the TableRow objects are read directly from
    // the table using the BigQuery API.
    runTestCreateBigQuerySource(
        "someproject", "somedataset", "sometable",
        makeCloudEncoding("BigEndianIntegerCoder"));
  }
}
