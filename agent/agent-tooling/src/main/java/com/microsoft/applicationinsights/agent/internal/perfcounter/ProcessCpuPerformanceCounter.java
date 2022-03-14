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

package com.microsoft.applicationinsights.agent.internal.perfcounter;

import static com.microsoft.applicationinsights.agent.internal.perfcounter.MetricNames.PROCESS_CPU;
import static com.microsoft.applicationinsights.agent.internal.perfcounter.MetricNames.PROCESS_CPU_NORMALIZED;

import com.azure.monitor.opentelemetry.exporter.implementation.utils.CpuPerformanceCounterCalculator;
import com.microsoft.applicationinsights.agent.internal.telemetry.TelemetryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The class supplies the cpu usage of the Java process the SDK is in. */
public class ProcessCpuPerformanceCounter implements PerformanceCounter {

  private static final Logger logger = LoggerFactory.getLogger(ProcessCpuPerformanceCounter.class);

  private CpuPerformanceCounterCalculator cpuPerformanceCounterCalculator;

  public ProcessCpuPerformanceCounter() {
    try {
      cpuPerformanceCounterCalculator = new CpuPerformanceCounterCalculator();
    } catch (ThreadDeath td) {
      throw td;
    } catch (Throwable t) {
      try {
        cpuPerformanceCounterCalculator = null;
        logger.error("Failed to create ProcessCpuPerformanceCounter", t);
      } catch (ThreadDeath td) {
        throw td;
      } catch (Throwable t2) {
        // chomp
      }
      throw new IllegalStateException("Failed to create ProcessCpuPerformanceCounter", t);
    }
  }

  @Override
  public void report(TelemetryClient telemetryClient) {
    if (cpuPerformanceCounterCalculator == null) {
      return;
    }
    Double processCpuUsage = cpuPerformanceCounterCalculator.getProcessCpuUsage();
    if (processCpuUsage == null) {
      return;
    }

    // unfortunately the Java SDK behavior has always been to report the "% Processor Time" number
    // as "normalized" (divided by # of CPU cores), even though it should be non-normalized
    // maybe this can be fixed in 4.0 (would be a breaking change)

    logger.trace("Performance Counter: {}: {}", PROCESS_CPU, processCpuUsage);
    telemetryClient.trackAsync(telemetryClient.newMetricTelemetry(PROCESS_CPU, processCpuUsage));

    logger.trace("Performance Counter: {}: {}", PROCESS_CPU_NORMALIZED, processCpuUsage);
    telemetryClient.trackAsync(
        telemetryClient.newMetricTelemetry(PROCESS_CPU_NORMALIZED, processCpuUsage));
  }
}
