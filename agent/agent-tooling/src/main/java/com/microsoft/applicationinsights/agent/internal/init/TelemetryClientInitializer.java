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

package com.microsoft.applicationinsights.agent.internal.init;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.azure.monitor.opentelemetry.exporter.implementation.configuration.ConnectionString;
import com.azure.monitor.opentelemetry.exporter.implementation.configuration.StatsbeatConnectionString;
import com.azure.monitor.opentelemetry.exporter.implementation.utils.Strings;
import com.microsoft.applicationinsights.agent.internal.common.PropertyHelper;
import com.microsoft.applicationinsights.agent.internal.configuration.Configuration;
import com.microsoft.applicationinsights.agent.internal.heartbeat.HeartBeatModule;
import com.microsoft.applicationinsights.agent.internal.perfcounter.DeadLockDetectorPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.FreeMemoryPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.GcPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.JmxAttributeData;
import com.microsoft.applicationinsights.agent.internal.perfcounter.JmxMetricPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.JvmHeapMemoryUsedPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.MetricNames;
import com.microsoft.applicationinsights.agent.internal.perfcounter.OshiPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.PerformanceCounterContainer;
import com.microsoft.applicationinsights.agent.internal.perfcounter.ProcessCpuPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.perfcounter.ProcessMemoryPerformanceCounter;
import com.microsoft.applicationinsights.agent.internal.telemetry.TelemetryClient;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Initializer class for telemetry client instances. */
public class TelemetryClientInitializer {

  private static final Logger logger = LoggerFactory.getLogger(TelemetryClientInitializer.class);

  public static void initialize(TelemetryClient telemetryClient, Configuration configuration) {

    setConnectionString(configuration, telemetryClient);
    setRoleName(configuration, telemetryClient);
    setRoleInstance(configuration, telemetryClient);

    addHeartBeatModule(configuration, telemetryClient);

    PerformanceCounterContainer.INSTANCE.setCollectionFrequencyInSec(
        configuration.preview.metricIntervalSeconds);

    if (logger.isDebugEnabled()) {
      PerformanceCounterContainer.INSTANCE.setLogAvailableJmxMetrics();
    }

    loadCustomJmxPerfCounters(configuration.jmxMetrics);

    PerformanceCounterContainer.INSTANCE.register(new ProcessCpuPerformanceCounter());
    PerformanceCounterContainer.INSTANCE.register(new ProcessMemoryPerformanceCounter());
    PerformanceCounterContainer.INSTANCE.register(new FreeMemoryPerformanceCounter());

    if (!isAgentRunningInSandboxEnvWindows()) {
      // system cpu and process disk i/o
      PerformanceCounterContainer.INSTANCE.register(new OshiPerformanceCounter());
    }

    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    if (threadBean.isSynchronizerUsageSupported()) {
      PerformanceCounterContainer.INSTANCE.register(new DeadLockDetectorPerformanceCounter());
    }
    PerformanceCounterContainer.INSTANCE.register(new JvmHeapMemoryUsedPerformanceCounter());
    PerformanceCounterContainer.INSTANCE.register(new GcPerformanceCounter());

    telemetryClient.addNonFilterableMetricNames(
        MetricNames.TOTAL_CPU,
        MetricNames.PROCESS_CPU,
        MetricNames.PROCESS_CPU_NORMALIZED,
        MetricNames.PROCESS_MEMORY,
        MetricNames.TOTAL_MEMORY,
        MetricNames.PROCESS_IO);

    telemetryClient.setQuickPulse(configuration, telemetryClient);
  }

  private static boolean isAgentRunningInSandboxEnvWindows() {
    String qualifiedSdkVersion = PropertyHelper.getQualifiedSdkVersionString();
    return qualifiedSdkVersion.startsWith("awr") || qualifiedSdkVersion.startsWith("fwr");
  }

  private static void setConnectionString(
      Configuration configuration, TelemetryClient telemetryClient) {

    String connectionString = configuration.connectionString;

    if (connectionString != null) {
      ConnectionString connectionStringObj = ConnectionString.parse(connectionString);
      telemetryClient.setConnectionString(connectionStringObj);
      telemetryClient.setStatsbeatConnectionString(
          StatsbeatConnectionString.create(
              connectionStringObj,
              configuration.internal.statsbeat.instrumentationKey,
              configuration.internal.statsbeat.endpoint));
    }
  }

  private static void setRoleName(Configuration configuration, TelemetryClient telemetryClient) {
    try {
      String roleName;

      // try to find the role name in ApplicationInsights.xml
      if (configuration != null) {
        roleName = configuration.role.name;
        if (roleName == null) {
          return;
        }

        roleName = roleName.trim();
        if (roleName.length() == 0) {
          return;
        }

        telemetryClient.setRoleName(roleName);
      }
    } catch (RuntimeException e) {
      logger.error("Failed to set role name: '{}'", e.toString());
    }
  }

  private static void setRoleInstance(
      Configuration configuration, TelemetryClient telemetryClient) {
    try {
      String roleInstance;

      // try to find the role instance in ApplicationInsights.xml
      if (configuration != null) {
        roleInstance = configuration.role.instance;
        if (roleInstance == null) {
          return;
        }

        roleInstance = roleInstance.trim();
        if (roleInstance.length() == 0) {
          return;
        }

        telemetryClient.setRoleInstance(roleInstance);
      }
    } catch (RuntimeException e) {
      logger.error("Failed to set role instance: '{}'", e.toString());
    }
  }

  /**
   * The method will load the Jmx performance counters requested by the user to the system: 1. Build
   * a map where the key is the Jmx object name and the value is a list of requested attributes. 2.
   * Go through all the requested Jmx counters: a. If the object name is not in the map, add it with
   * an empty list Else get the list b. Add the attribute to the list. 3. Go through the map For
   * every entry (object name and attributes) Build a {@link JmxMetricPerformanceCounter} Register
   * the Performance Counter in the {@link PerformanceCounterContainer}
   */
  private static void loadCustomJmxPerfCounters(List<Configuration.JmxMetric> jmxXmlElements) {
    try {
      if (jmxXmlElements == null) {
        return;
      }

      HashMap<String, Collection<JmxAttributeData>> data = new HashMap<>();

      // Build a map of object name to its requested attributes
      for (Configuration.JmxMetric jmxElement : jmxXmlElements) {
        Collection<JmxAttributeData> collection =
            data.computeIfAbsent(jmxElement.objectName, k -> new ArrayList<>());

        if (Strings.isNullOrEmpty(jmxElement.objectName)) {
          logger.error("JMX object name is empty, will be ignored");
          continue;
        }

        if (Strings.isNullOrEmpty(jmxElement.attribute)) {
          logger.error("JMX attribute is empty for '{}', will be ignored", jmxElement.objectName);
          continue;
        }

        if (Strings.isNullOrEmpty(jmxElement.name)) {
          logger.error("JMX name is empty for '{}', will be ignored", jmxElement.objectName);
          continue;
        }

        collection.add(new JmxAttributeData(jmxElement.name, jmxElement.attribute));
      }

      // Register each entry in the performance container
      for (Map.Entry<String, Collection<JmxAttributeData>> entry : data.entrySet()) {
        try {
          PerformanceCounterContainer.INSTANCE.register(
              new JmxMetricPerformanceCounter(entry.getKey(), entry.getValue()));
        } catch (RuntimeException e) {
          logger.error(
              "Failed to register JMX performance counter '{}': '{}'",
              entry.getKey(),
              e.toString());
        }
      }
    } catch (RuntimeException e) {
      logger.error("Failed to register JMX performance counters: '{}'", e.toString());
    }
  }

  /**
   * Adds heartbeat module with default configuration.
   *
   * @param telemetryClient telemetry client instance
   */
  private static void addHeartBeatModule(
      Configuration configuration, TelemetryClient telemetryClient) {
    HeartBeatModule module = new HeartBeatModule();

    // do not allow interval longer than 15 minutes, since we use the heartbeat data for usage
    // telemetry
    long intervalSeconds = Math.min(configuration.heartbeat.intervalSeconds, MINUTES.toSeconds(15));

    module.setHeartBeatInterval(intervalSeconds);

    module.initialize(telemetryClient);
  }

  private TelemetryClientInitializer() {}
}
