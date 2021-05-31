/*
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
package org.apache.beam.examples.complete.kafkatomysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.beam.examples.complete.kafkatomysql.options.KafkaToMysqlOptions;
import org.apache.beam.runners.direct.DirectOptions;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaToMySqlE2ETest {
	private static final String KAFKA_IMAGE_NAME = "confluentinc/cp-kafka:5.4.3";
	private static final String MYSQL_IMAGE_NAME = "mysql:8.0.22";
	private static final String MESSAGE = "test message";
	private static final String KAFKA_TOPIC_NAME = "messages-topic";
	private static final PipelineOptions OPTIONS = TestPipeline.testingPipelineOptions();

	@ClassRule
	public static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE_NAME)
		.withInitScript("org/apache/beam/examples/complete/kafkatomysql/init_mysql.sql");

	@ClassRule
	public static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME));

	@Rule
	public final transient TestPipeline pipeline = TestPipeline.fromOptions(OPTIONS);

	@BeforeClass
	public static void beforeClass() throws Exception {
		OPTIONS.as(DirectOptions.class).setBlockOnRun(false);
		OPTIONS
			.as(KafkaToMysqlOptions.class)
			.setBootstrapServers(KAFKA_CONTAINER.getBootstrapServers());
		OPTIONS.as(KafkaToMysqlOptions.class).setInputTopics(KAFKA_TOPIC_NAME);
		OPTIONS
			.as(KafkaToMysqlOptions.class)
			.setKafkaConsumerConfig(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG + "=earliest");
		OPTIONS
			.as(KafkaToMysqlOptions.class)
			.setMySqlConnectionString(MYSQL_CONTAINER.getJdbcUrl());

	}

	@Test
	public void testKafkaToMysqlE2E() throws Exception {
		PipelineResult job = KafkaToMysql.run(pipeline, OPTIONS.as(KafkaToMysqlOptions.class));
		sendKafkaMessage();

		try (Connection connection = MYSQL_CONTAINER.createConnection("")) {

			job.waitUntilFinish(Duration.standardSeconds(30));
			ResultSet resultSet = connection.createStatement().executeQuery("select count(*) from test");
			resultSet.next();
			int count = resultSet.getInt(1);
			Assert.assertEquals(3, count);
			Assert.assertEquals(PipelineResult.State.RUNNING, job.getState());

		} catch (UnsupportedOperationException e) {
			throw new AssertionError("Could not stop pipeline.", e);
		} finally {
			job.cancel();
		}
	}

	private void sendKafkaMessage() {
		try (KafkaProducer<String, String> producer =
			new KafkaProducer<>(
				ImmutableMap.of(
					ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
					KAFKA_CONTAINER.getBootstrapServers(),
					ProducerConfig.CLIENT_ID_CONFIG,
					UUID.randomUUID().toString()),
				new StringSerializer(),
				new StringSerializer())) {
			producer.send(new ProducerRecord<>(KAFKA_TOPIC_NAME, "testcontainers", MESSAGE)).get();
			producer.send(new ProducerRecord<>(KAFKA_TOPIC_NAME, "testcontainers1", MESSAGE)).get();
			producer.send(new ProducerRecord<>(KAFKA_TOPIC_NAME, "testcontainers2", MESSAGE)).get();
			producer.flush();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException("Something went wrong in kafka producer", e);
		}
	}
}
