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

import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpRequest;
import com.azure.monitor.opentelemetry.exporter.implementation.models.TelemetryItem;
import com.azure.monitor.opentelemetry.exporter.implementation.utils.HostName;
import com.azure.monitor.opentelemetry.exporter.implementation.utils.Strings;
import com.azure.monitor.opentelemetry.exporter.implementation.utils.ThreadPoolUtils;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class QuickPulse {

  static final int QP_INVARIANT_VERSION = 1;
  private volatile boolean initialized = false;

  private volatile QuickPulseDataCollector collector;

  public static QuickPulse create(
      HttpPipeline httpPipeline,
      Supplier<URL> endpointUrl,
      Supplier<String> instrumentationKey,
      @Nullable String roleName,
      @Nullable String roleInstance,
      boolean backCompatNonNormalizedCpuPercentage) {
    QuickPulse quickPulse = new QuickPulse();
    quickPulse.initialize(
        httpPipeline,
        endpointUrl,
        instrumentationKey,
        roleName,
        roleInstance,
        backCompatNonNormalizedCpuPercentage);
    return quickPulse;
  }

  // initialization is performed in the background because initializing the random seed (via
  // UUID.randomUUID()) below
  // can cause slowness during startup in some environments

  private void initialize(
      HttpPipeline httpPipeline,
      Supplier<URL> endpointUrl,
      Supplier<String> instrumentationKey,
      @Nullable String roleName,
      @Nullable String roleInstance,
      boolean backCompatNonNormalizedCpuPercentage) {
    CountDownLatch latch = new CountDownLatch(1);
    Executors.newSingleThreadExecutor(ThreadPoolUtils.createDaemonThreadFactory(QuickPulse.class))
        .execute(
            () ->
                initializeSync(
                    latch,
                    httpPipeline,
                    endpointUrl,
                    instrumentationKey,
                    roleName,
                    roleInstance,
                    backCompatNonNormalizedCpuPercentage));
    // don't return until initialization thread has lock
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void add(TelemetryItem telemetryItem) {
    if (collector != null) {
      collector.add(telemetryItem);
    }
  }

  private void initializeSync(
      CountDownLatch latch,
      HttpPipeline httpPipeline,
      Supplier<URL> endpointUrl,
      Supplier<String> instrumentationKey,
      @Nullable String roleName,
      @Nullable String roleInstance,
      boolean backCompatNonNormalizedCpuPercentage) {
    if (initialized) {
      latch.countDown();
    } else {
      synchronized (this) {
        latch.countDown();
        if (!initialized) {
          initialized = true;
          String quickPulseId = UUID.randomUUID().toString().replace("-", "");
          ArrayBlockingQueue<HttpRequest> sendQueue = new ArrayBlockingQueue<>(256, true);

          QuickPulseDataSender quickPulseDataSender =
              new QuickPulseDataSender(httpPipeline, sendQueue);

          String instanceName = roleInstance;
          String machineName = HostName.get();

          if (Strings.isNullOrEmpty(instanceName)) {
            instanceName = machineName;
          }
          if (Strings.isNullOrEmpty(instanceName)) {
            instanceName = "Unknown host";
          }

          QuickPulseDataCollector collector =
              new QuickPulseDataCollector(backCompatNonNormalizedCpuPercentage);

          QuickPulsePingSender quickPulsePingSender =
              new QuickPulsePingSender(
                  httpPipeline,
                  endpointUrl,
                  instrumentationKey,
                  roleName,
                  instanceName,
                  machineName,
                  quickPulseId);
          QuickPulseDataFetcher quickPulseDataFetcher =
              new QuickPulseDataFetcher(
                  collector,
                  sendQueue,
                  endpointUrl,
                  instrumentationKey,
                  roleName,
                  instanceName,
                  machineName,
                  quickPulseId);

          QuickPulseCoordinatorInitData coordinatorInitData =
              new QuickPulseCoordinatorInitDataBuilder()
                  .withPingSender(quickPulsePingSender)
                  .withDataFetcher(quickPulseDataFetcher)
                  .withDataSender(quickPulseDataSender)
                  .withCollector(collector)
                  .build();

          QuickPulseCoordinator coordinator = new QuickPulseCoordinator(coordinatorInitData);

          Thread senderThread =
              new Thread(quickPulseDataSender, QuickPulseDataSender.class.getSimpleName());
          senderThread.setDaemon(true);
          senderThread.start();

          Thread thread = new Thread(coordinator, QuickPulseCoordinator.class.getSimpleName());
          thread.setDaemon(true);
          thread.start();

          collector.enable(instrumentationKey);

          this.collector = collector;
        }
      }
    }
  }
}
