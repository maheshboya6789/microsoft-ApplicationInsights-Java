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

package com.microsoft.applicationinsights.agent.internal.exporter;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import com.azure.monitor.opentelemetry.exporter.implementation.builders.AbstractTelemetryBuilder;
import com.azure.monitor.opentelemetry.exporter.implementation.models.ContextTagKeys;
import com.microsoft.applicationinsights.agent.internal.exporter.utils.Trie;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

// common string constants and static methods shared among exporters
final class ExporterUtil {

  // TODO (trask) this can go away once new indexer is rolled out to gov clouds
  private static final AttributeKey<List<String>> AI_REQUEST_CONTEXT_KEY =
      AttributeKey.stringArrayKey("http.response.header.request_context");
  private static final AttributeKey<Long> KAFKA_OFFSET = longKey("kafka.offset");

  private static final Trie<Boolean> STANDARD_ATTRIBUTE_PREFIX_TRIE;

  static final AttributeKey<String> AZURE_NAMESPACE = AttributeKey.stringKey("az.namespace");
  static final AttributeKey<String> AZURE_SDK_MESSAGE_BUS_DESTINATION =
      AttributeKey.stringKey("message_bus.destination");
  static final AttributeKey<Long> AZURE_SDK_ENQUEUED_TIME =
      AttributeKey.longKey("x-opt-enqueued-time");
  static final AttributeKey<Long> KAFKA_RECORD_QUEUE_TIME_MS =
      longKey("kafka.record.queue_time_ms");

  static {
    // TODO need to keep this list in sync as new semantic conventions are defined
    STANDARD_ATTRIBUTE_PREFIX_TRIE =
        Trie.<Boolean>newBuilder()
            .put("http.", true)
            .put("db.", true)
            .put("message.", true)
            .put("messaging.", true)
            .put("rpc.", true)
            .put("enduser.", true)
            .put("net.", true)
            .put("peer.", true)
            .put("exception.", true)
            .put("thread.", true)
            .put("faas.", true)
            .build();
  }

  static void setExtraAttributes(
      AbstractTelemetryBuilder telemetryBuilder, Attributes attributes, Logger logger) {
    attributes.forEach(
        (key, value) -> {
          String stringKey = key.getKey();
          if (stringKey.startsWith("applicationinsights.internal.")) {
            return;
          }
          if (stringKey.equals(AZURE_NAMESPACE.getKey())
              || stringKey.equals(AZURE_SDK_MESSAGE_BUS_DESTINATION.getKey())
              || stringKey.equals(AZURE_SDK_ENQUEUED_TIME.getKey())) {
            // these are from azure SDK (AZURE_SDK_PEER_ADDRESS gets filtered out automatically
            // since it uses the otel "peer." prefix)
            return;
          }
          if (stringKey.equals(KAFKA_RECORD_QUEUE_TIME_MS.getKey())
              || stringKey.equals(KAFKA_OFFSET.getKey())) {
            return;
          }
          if (stringKey.equals(AI_REQUEST_CONTEXT_KEY.getKey())) {
            return;
          }
          // special case mappings
          if (stringKey.equals(SemanticAttributes.ENDUSER_ID.getKey()) && value instanceof String) {
            telemetryBuilder.addTag(ContextTagKeys.AI_USER_ID.toString(), (String) value);
            return;
          }
          if (stringKey.equals(SemanticAttributes.HTTP_USER_AGENT.getKey())
              && value instanceof String) {
            telemetryBuilder.addTag("ai.user.userAgent", (String) value);
            return;
          }
          if (stringKey.equals("ai.preview.instrumentation_key") && value instanceof String) {
            telemetryBuilder.setInstrumentationKey((String) value);
            return;
          }
          if (stringKey.equals("ai.preview.service_name") && value instanceof String) {
            telemetryBuilder.addTag(ContextTagKeys.AI_CLOUD_ROLE.toString(), (String) value);
            return;
          }
          if (stringKey.equals("ai.preview.service_instance_id") && value instanceof String) {
            telemetryBuilder.addTag(
                ContextTagKeys.AI_CLOUD_ROLE_INSTANCE.toString(), (String) value);
            return;
          }
          if (stringKey.equals("ai.preview.service_version") && value instanceof String) {
            telemetryBuilder.addTag(ContextTagKeys.AI_APPLICATION_VER.toString(), (String) value);
            return;
          }
          if (STANDARD_ATTRIBUTE_PREFIX_TRIE.getOrDefault(stringKey, false)
              && !stringKey.startsWith("http.request.header.")
              && !stringKey.startsWith("http.response.header.")) {
            return;
          }
          String val = convertToString(value, key.getType(), logger);
          if (value != null) {
            telemetryBuilder.addProperty(key.getKey(), val);
          }
        });
  }

  @Nullable
  private static String convertToString(Object value, AttributeType type, Logger logger) {
    switch (type) {
      case STRING:
      case BOOLEAN:
      case LONG:
      case DOUBLE:
        return String.valueOf(value);
      case STRING_ARRAY:
      case BOOLEAN_ARRAY:
      case LONG_ARRAY:
      case DOUBLE_ARRAY:
        return join((List<?>) value);
    }
    logger.warn("unexpected attribute type: {}", type);
    return null;
  }

  private static <T> String join(List<T> values) {
    StringBuilder sb = new StringBuilder();
    for (Object val : values) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(val);
    }
    return sb.toString();
  }

  private ExporterUtil() {}
}