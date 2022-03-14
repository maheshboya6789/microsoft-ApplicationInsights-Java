/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.azure.monitor.opentelemetry.exporter.implementation.quickpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.azure.core.http.HttpRequest;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.monitor.opentelemetry.exporter.implementation.configuration.ConnectionString;
import com.azure.monitor.opentelemetry.exporter.implementation.models.TelemetryItem;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class QuickPulseIntegrationTests extends QuickPulseTestBase {
  private static final ConnectionString connectionString =
      ConnectionString.parse("InstrumentationKey=ikey123");
  private static final String instrumentationKey = "ikey123";

  private QuickPulsePingSender getQuickPulsePingSender() {
    return new QuickPulsePingSender(
        getHttpPipeline(),
        connectionString::getLiveEndpoint,
        connectionString::getInstrumentationKey,
        null,
        "instance1",
        "machine1",
        "qpid123");
  }

  private QuickPulsePingSender getQuickPulsePingSenderWithAuthentication() {
    return new QuickPulsePingSender(
        getHttpPipelineWithAuthentication(),
        connectionString::getLiveEndpoint,
        connectionString::getInstrumentationKey,
        null,
        "instance1",
        "machine1",
        "qpid123");
  }

  private QuickPulsePingSender getQuickPulsePingSenderWithValidator(HttpPipelinePolicy validator) {
    return new QuickPulsePingSender(
        getHttpPipeline(validator),
        connectionString::getLiveEndpoint,
        connectionString::getInstrumentationKey,
        null,
        "instance1",
        "machine1",
        "qpid123");
  }

  @Test
  public void testPing() {
    QuickPulsePingSender quickPulsePingSender = getQuickPulsePingSender();
    QuickPulseHeaderInfo quickPulseHeaderInfo = quickPulsePingSender.ping(null);
    assertThat(quickPulseHeaderInfo.getQuickPulseStatus()).isEqualTo(QuickPulseStatus.QP_IS_ON);
  }

  @Test
  public void testPingWithAuthentication() {
    QuickPulsePingSender quickPulsePingSender = getQuickPulsePingSenderWithAuthentication();
    QuickPulseHeaderInfo quickPulseHeaderInfo = quickPulsePingSender.ping(null);
    assertThat(quickPulseHeaderInfo.getQuickPulseStatus()).isEqualTo(QuickPulseStatus.QP_IS_ON);
  }

  @Test
  public void testPingRequestBody() throws InterruptedException {
    CountDownLatch pingCountDown = new CountDownLatch(1);
    String expectedRequestBody =
        "\\{\"Documents\":null,\"InstrumentationKey\":null,\"Metrics\":null,\"InvariantVersion\":1,\"Timestamp\":\"\\\\/Date\\(\\d+\\)\\\\/\",\"Version\":\"\\(unknown\\)\",\"StreamId\":\"qpid123\",\"MachineName\":\"machine1\",\"Instance\":\"instance1\",\"RoleName\":null\\}";
    QuickPulsePingSender quickPulsePingSender =
        getQuickPulsePingSenderWithValidator(
            new ValidationPolicy(pingCountDown, expectedRequestBody));
    QuickPulseHeaderInfo quickPulseHeaderInfo = quickPulsePingSender.ping(null);
    assertThat(quickPulseHeaderInfo.getQuickPulseStatus()).isEqualTo(QuickPulseStatus.QP_IS_ON);
    assertTrue(pingCountDown.await(60, TimeUnit.SECONDS));
  }

  @Test
  public void testPostRequest() throws InterruptedException {
    ArrayBlockingQueue<HttpRequest> sendQueue = new ArrayBlockingQueue<>(256, true);
    CountDownLatch pingCountDown = new CountDownLatch(1);
    CountDownLatch postCountDown = new CountDownLatch(1);
    Date currDate = new Date();
    String expectedPingRequestBody =
        "\\{\"Documents\":null,\"InstrumentationKey\":null,\"Metrics\":null,\"InvariantVersion\":1,\"Timestamp\":\"\\\\/Date\\(\\d+\\)\\\\/\",\"Version\":\"\\(unknown\\)\",\"StreamId\":\"qpid123\",\"MachineName\":\"machine1\",\"Instance\":\"instance1\",\"RoleName\":null\\}";
    String expectedPostRequestBody =
        "\\[\\{\"Documents\":\\[\\{\"__type\":\"RequestTelemetryDocument\",\"DocumentType\":\"Request\",\"Version\":\"1.0\",\"OperationId\":null,\"Properties\":\\{\"customProperty\":\"customValue\"\\},\"Name\":\"request-test\",\"Success\":true,\"Duration\":\"PT.*S\",\"ResponseCode\":\"200\",\"OperationName\":null,\"Url\":\"foo\"\\},\\{\"__type\":\"DependencyTelemetryDocument\",\"DocumentType\":\"RemoteDependency\",\"Version\":\"1.0\",\"OperationId\":null,\"Properties\":\\{\"customProperty\":\"customValue\"\\},\"Name\":\"dep-test\",\"Target\":null,\"Success\":true,\"Duration\":\"PT.*S\",\"ResultCode\":null,\"CommandName\":\"dep-test-cmd\",\"DependencyTypeName\":null,\"OperationName\":null\\},\\{\"__type\":\"ExceptionTelemetryDocument\",\"DocumentType\":\"Exception\",\"Version\":\"1.0\",\"OperationId\":null,\"Properties\":null,\"Exception\":\"\",\"ExceptionMessage\":\"test\",\"ExceptionType\":\"java.lang.Exception\"\\}\\],\"InstrumentationKey\":\""
            + instrumentationKey
            + "\",\"Metrics\":\\[\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Requests\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Request Duration\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Requests Failed\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Requests Succeeded\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Dependency Calls\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Dependency Call Duration\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Dependency Calls Failed\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Dependency Calls Succeeded\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\ApplicationInsights\\\\\\\\Exceptions\\\\\\/Sec\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\Memory\\\\\\\\Committed Bytes\",\"Value\":\\d+,\"Weight\":\\d+\\},\\{\"Name\":\"\\\\\\\\Processor\\(_Total\\)\\\\\\\\% Processor Time\",\"Value\":-?\\d+,\"Weight\":\\d+\\}\\],\"InvariantVersion\":1,\"Timestamp\":\"\\\\\\/Date\\(\\d+\\)\\\\\\/\",\"Version\":\"[^\"]*\",\"StreamId\":null,\"MachineName\":\"machine1\",\"Instance\":\"instance1\",\"RoleName\":null\\}\\]";
    QuickPulsePingSender pingSender =
        getQuickPulsePingSenderWithValidator(
            new ValidationPolicy(pingCountDown, expectedPingRequestBody));
    QuickPulseHeaderInfo quickPulseHeaderInfo = pingSender.ping(null);
    QuickPulseDataSender dataSender =
        new QuickPulseDataSender(
            getHttpPipeline(new ValidationPolicy(postCountDown, expectedPostRequestBody)),
            sendQueue);
    QuickPulseDataCollector collector = new QuickPulseDataCollector(false);
    QuickPulseDataFetcher dataFetcher =
        new QuickPulseDataFetcher(
            collector,
            sendQueue,
            connectionString::getLiveEndpoint,
            connectionString::getInstrumentationKey,
            null,
            "instance1",
            "machine1",
            null);

    collector.setQuickPulseStatus(QuickPulseStatus.QP_IS_ON);
    collector.enable(connectionString::getInstrumentationKey);
    final long duration = 112233L;
    // Request Telemetry
    TelemetryItem requestTelemetry =
        createRequestTelemetry("request-test", currDate, duration, "200", true);
    requestTelemetry.setInstrumentationKey(instrumentationKey);
    collector.add(requestTelemetry);
    // Dependency Telemetry
    TelemetryItem dependencyTelemetry =
        createRemoteDependencyTelemetry("dep-test", "dep-test-cmd", duration, true);
    dependencyTelemetry.setInstrumentationKey(instrumentationKey);
    collector.add(dependencyTelemetry);
    // Exception Telemetry
    TelemetryItem exceptionTelemetry = createExceptionTelemetry(new Exception("test"));
    exceptionTelemetry.setInstrumentationKey(instrumentationKey);
    collector.add(exceptionTelemetry);

    QuickPulseCoordinatorInitData initData =
        new QuickPulseCoordinatorInitDataBuilder()
            .withDataFetcher(dataFetcher)
            .withDataSender(dataSender)
            .withPingSender(pingSender)
            .withCollector(collector)
            .withWaitBetweenPingsInMillis(10L)
            .withWaitBetweenPostsInMillis(10L)
            .withWaitOnErrorInMillis(10L)
            .build();
    QuickPulseCoordinator coordinator = new QuickPulseCoordinator(initData);

    Thread coordinatorThread = new Thread(coordinator, QuickPulseCoordinator.class.getSimpleName());
    coordinatorThread.setDaemon(true);
    coordinatorThread.start();

    Thread senderThread = new Thread(dataSender, QuickPulseDataSender.class.getSimpleName());
    senderThread.setDaemon(true);
    senderThread.start();
    Thread.sleep(50);
    assertTrue(pingCountDown.await(1, TimeUnit.SECONDS));
    assertThat(quickPulseHeaderInfo.getQuickPulseStatus()).isEqualTo(QuickPulseStatus.QP_IS_ON);
    assertThat(collector.getQuickPulseStatus()).isEqualTo(QuickPulseStatus.QP_IS_ON);
    assertTrue(postCountDown.await(1, TimeUnit.SECONDS));
    senderThread.interrupt();
    coordinatorThread.interrupt();
  }
}
