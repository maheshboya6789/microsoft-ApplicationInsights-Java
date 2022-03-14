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

package com.microsoft.applicationinsights.agent.internal.configuration;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.status.StatusFile;
import com.microsoft.applicationinsights.agent.internal.common.FriendlyException;
import io.opentelemetry.api.common.AttributeKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

// an assumption is made throughout this file that user will not explicitly use `null` value in json
// file
// TODO how to pre-process or generally be robust in the face of explicit `null` value usage?
public class Configuration {

  public String connectionString;
  public Role role = new Role();
  public Map<String, String> customDimensions = new HashMap<>();
  public Sampling sampling = new Sampling();
  public List<JmxMetric> jmxMetrics = new ArrayList<>();
  public Instrumentation instrumentation = new Instrumentation();
  public Heartbeat heartbeat = new Heartbeat();
  public Proxy proxy = new Proxy();
  public SelfDiagnostics selfDiagnostics = new SelfDiagnostics();
  public PreviewConfiguration preview = new PreviewConfiguration();
  public InternalConfiguration internal = new InternalConfiguration();

  // this is just here to detect if using old format in order to give a helpful error message
  public Map<String, Object> instrumentationSettings;

  private static boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  // TODO (trask) investigate options for mapping lowercase values to otel enum directly
  public enum SpanKind {
    @JsonProperty("server")
    SERVER(io.opentelemetry.api.trace.SpanKind.SERVER),
    @JsonProperty("client")
    CLIENT(io.opentelemetry.api.trace.SpanKind.CLIENT),
    @JsonProperty("consumer")
    CONSUMER(io.opentelemetry.api.trace.SpanKind.CONSUMER),
    @JsonProperty("producer")
    PRODUCER(io.opentelemetry.api.trace.SpanKind.PRODUCER),
    @JsonProperty("internal")
    INTERNAL(io.opentelemetry.api.trace.SpanKind.INTERNAL);

    public final io.opentelemetry.api.trace.SpanKind otelSpanKind;

    SpanKind(io.opentelemetry.api.trace.SpanKind otelSpanKind) {
      this.otelSpanKind = otelSpanKind;
    }
  }

  public enum MatchType {
    @JsonProperty("strict")
    STRICT,
    @JsonProperty("regexp")
    REGEXP
  }

  public enum ProcessorActionType {
    @JsonProperty("insert")
    INSERT,
    @JsonProperty("update")
    UPDATE,
    @JsonProperty("delete")
    DELETE,
    @JsonProperty("hash")
    HASH,
    @JsonProperty("extract")
    EXTRACT,
    @JsonProperty("mask")
    MASK
  }

  public enum ProcessorType {
    @JsonProperty("attribute")
    ATTRIBUTE("an attribute"),
    @JsonProperty("log")
    LOG("a log"),
    @JsonProperty("span")
    SPAN("a span"),
    @JsonProperty("metric-filter")
    METRIC_FILTER("a metric-filter");

    private final String anX;

    ProcessorType(String anX) {
      this.anX = anX;
    }
  }

  private enum IncludeExclude {
    INCLUDE,
    EXCLUDE;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static class Role {

    public String name;
    public String instance;
  }

  public static class Sampling {

    public float percentage = 100;
  }

  public static class SamplingPreview {

    public List<SamplingOverride> overrides = new ArrayList<>();
  }

  public static class JmxMetric {

    public String name;
    public String objectName;
    public String attribute;
  }

  public static class Instrumentation {

    public EnabledByDefaultInstrumentation azureSdk = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation cassandra = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation jdbc = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation jms = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation kafka = new EnabledByDefaultInstrumentation();
    public LoggingInstrumentation logging = new LoggingInstrumentation();
    public MicrometerInstrumentation micrometer = new MicrometerInstrumentation();
    public EnabledByDefaultInstrumentation mongo = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation rabbitmq = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation redis = new EnabledByDefaultInstrumentation();
    public EnabledByDefaultInstrumentation springScheduling = new EnabledByDefaultInstrumentation();
  }

  public static class LoggingInstrumentation {
    public String level = "INFO";
  }

  public static class MicrometerInstrumentation {
    public boolean enabled = true;
    // this is just here to detect if using this old undocumented setting in order to give a helpful
    // error message
    @Deprecated public int reportingIntervalSeconds = 60;
  }

  public static class Heartbeat {
    public long intervalSeconds = MINUTES.toSeconds(15);
  }

  public static class Statsbeat {
    // disabledAll is used internally as an emergency kill-switch to turn off Statsbeat completely
    // when something goes wrong.
    public boolean disabledAll = false;

    public String instrumentationKey;
    public String endpoint;
    public long shortIntervalSeconds = MINUTES.toSeconds(15); // default to 15 minutes
    public long longIntervalSeconds = DAYS.toSeconds(1); // default to daily
  }

  public static class Proxy {

    public String host;
    public int port = 80;
    public String username;
    public String password;
  }

  public static class PreviewConfiguration {

    public SamplingPreview sampling = new SamplingPreview();
    public List<ProcessorConfig> processors = new ArrayList<>();
    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated public boolean openTelemetryApiSupport;
    public PreviewInstrumentation instrumentation = new PreviewInstrumentation();
    // applies to perf counters, default custom metrics, jmx metrics, and micrometer metrics
    // not sure if we'll be able to have different metric intervals in future OpenTelemetry metrics
    // world,
    // so safer to only allow single interval for now
    public int metricIntervalSeconds = 60;
    // ignoreRemoteParentNotSampled is sometimes needed because .NET SDK always propagates trace
    // flags "00" (not sampled)
    // in particular, it is always needed in Azure Functions worker
    public boolean ignoreRemoteParentNotSampled = DiagnosticsHelper.rpIntegrationChar() == 'f';
    public boolean captureControllerSpans;
    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated public boolean httpMethodInOperationName;
    public LiveMetrics liveMetrics = new LiveMetrics();
    public LegacyRequestIdPropagation legacyRequestIdPropagation = new LegacyRequestIdPropagation();
    // this is needed to unblock customer, but is not the ideal long-term solution
    // https://portal.microsofticm.com/imp/v3/incidents/details/266992200/home
    public boolean disablePropagation;
    public boolean captureHttpServer4xxAsError = true;
    // this is to support interoperability with other systems
    // intentionally not allowing the removal of w3c propagator since that is key to many Azure
    // integrated experiences
    public List<String> additionalPropagators = new ArrayList<>();

    public List<InheritedAttribute> inheritedAttributes = new ArrayList<>();

    public HttpHeadersConfiguration captureHttpServerHeaders = new HttpHeadersConfiguration();
    public HttpHeadersConfiguration captureHttpClientHeaders = new HttpHeadersConfiguration();

    public ProfilerConfiguration profiler = new ProfilerConfiguration();
    public GcEventConfiguration gcEvents = new GcEventConfiguration();
    public AadAuthentication authentication = new AadAuthentication();
    public PreviewStatsbeat statsbeat = new PreviewStatsbeat();

    public List<InstrumentationKeyOverride> instrumentationKeyOverrides = new ArrayList<>();
    public List<RoleNameOverride> roleNameOverrides = new ArrayList<>();

    public int generalExportQueueCapacity = 2048;
    // metrics get flooded every 60 seconds by default, so need larger queue size to avoid dropping
    // telemetry (they are much smaller so a larger queue size is ok)
    public int metricsExportQueueCapacity = 65536;

    // unfortunately the Java SDK behavior has always been to report the "% Processor Time" number
    // as "normalized" (divided by # of CPU cores), even though it should be non-normalized
    // maybe this can be fixed in 4.0 (would be a breaking change)
    public boolean reportNonNormalizedProcessorTime;

    private static final Set<String> VALID_ADDITIONAL_PROPAGATORS =
        new HashSet<>(asList("b3", "b3multi"));

    public void validate() {
      for (Configuration.SamplingOverride samplingOverride : sampling.overrides) {
        samplingOverride.validate();
      }
      for (Configuration.InstrumentationKeyOverride instrumentationKeyOverride :
          instrumentationKeyOverrides) {
        instrumentationKeyOverride.validate();
      }
      for (Configuration.RoleNameOverride roleNameOverride : roleNameOverrides) {
        roleNameOverride.validate();
      }
      for (ProcessorConfig processorConfig : processors) {
        processorConfig.validate();
      }
      authentication.validate();

      for (String additionalPropagator : additionalPropagators) {
        if (!VALID_ADDITIONAL_PROPAGATORS.contains(additionalPropagator)) {
          throw new FriendlyException(
              "The \"additionalPropagators\" configuration contains an invalid entry: "
                  + additionalPropagator,
              "Please provide only valid values for \"additionalPropagators\" configuration.");
        }
      }
    }
  }

  public static class InheritedAttribute {
    public String key;
    public SpanAttributeType type;

    public AttributeKey<?> getAttributeKey() {
      switch (type) {
        case STRING:
          return AttributeKey.stringKey(key);
        case BOOLEAN:
          return AttributeKey.booleanKey(key);
        case LONG:
          return AttributeKey.longKey(key);
        case DOUBLE:
          return AttributeKey.doubleKey(key);
        case STRING_ARRAY:
          return AttributeKey.stringArrayKey(key);
        case BOOLEAN_ARRAY:
          return AttributeKey.booleanArrayKey(key);
        case LONG_ARRAY:
          return AttributeKey.longArrayKey(key);
        case DOUBLE_ARRAY:
          return AttributeKey.doubleArrayKey(key);
      }
      throw new IllegalStateException("Unexpected attribute key type: " + type);
    }
  }

  public static class HttpHeadersConfiguration {
    public List<String> requestHeaders = new ArrayList<>();
    public List<String> responseHeaders = new ArrayList<>();
  }

  public enum SpanAttributeType {
    @JsonProperty("string")
    STRING,
    @JsonProperty("boolean")
    BOOLEAN,
    @JsonProperty("long")
    LONG,
    @JsonProperty("double")
    DOUBLE,
    @JsonProperty("string-array")
    STRING_ARRAY,
    @JsonProperty("boolean-array")
    BOOLEAN_ARRAY,
    @JsonProperty("long-array")
    LONG_ARRAY,
    @JsonProperty("double-array")
    DOUBLE_ARRAY
  }

  public static class LegacyRequestIdPropagation {
    public boolean enabled;
  }

  public static class InternalConfiguration {
    // This is used for collecting internal stats
    public Statsbeat statsbeat = new Statsbeat();
  }

  public static class PreviewInstrumentation {

    public DisabledByDefaultInstrumentation play = new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation akka = new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation apacheCamel = new DisabledByDefaultInstrumentation();

    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated
    public DisabledByDefaultInstrumentation azureSdk = new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation grizzly = new DisabledByDefaultInstrumentation();

    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated
    public DisabledByDefaultInstrumentation javaHttpClient = new DisabledByDefaultInstrumentation();

    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated
    public DisabledByDefaultInstrumentation jaxws = new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation quartz = new DisabledByDefaultInstrumentation();

    // this is just here to detect if using this old setting in order to give a helpful message
    @Deprecated
    public DisabledByDefaultInstrumentation rabbitmq = new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation springIntegration =
        new DisabledByDefaultInstrumentation();

    public DisabledByDefaultInstrumentation vertx = new DisabledByDefaultInstrumentation();
  }

  public static class PreviewStatsbeat {
    // disabled is used by customer to turn off non-essential Statsbeat, e.g. disk persistence
    // operation status, optional network statsbeat, other endpoints except Breeze, etc.
    public boolean disabled = false;
  }

  public static class InstrumentationKeyOverride {
    public String httpPathPrefix;
    public String instrumentationKey;

    public void validate() {
      if (httpPathPrefix == null) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A instrumentation key override configuration is missing an \"httpPathPrefix\".",
            "Please provide an \"httpPathPrefix\" for the instrumentation key override configuration.");
      }
      if (instrumentationKey == null) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "An instrumentation key override configuration is missing an \"instrumentationKey\".",
            "Please provide an \"instrumentationKey\" for the instrumentation key override configuration.");
      }
    }
  }

  public static class RoleNameOverride {
    public String httpPathPrefix;
    public String roleName;

    public void validate() {
      if (httpPathPrefix == null) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A role name override configuration is missing an \"httpPathPrefix\".",
            "Please provide an \"httpPathPrefix\" for the role name override configuration.");
      }
      if (roleName == null) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "An role name override configuration is missing a \"roleName\".",
            "Please provide a \"roleName\" for the role name override configuration.");
      }
    }
  }

  public static class EnabledByDefaultInstrumentation {
    public boolean enabled = true;
  }

  public static class DisabledByDefaultInstrumentation {
    public boolean enabled;
  }

  public static class LiveMetrics {
    public boolean enabled = true;
  }

  public static class SelfDiagnostics {

    public String level = "info";
    public String destination = "file+console";
    public DestinationFile file = new DestinationFile();
  }

  public static class DestinationFile {

    private static final String DEFAULT_NAME = "applicationinsights.log";

    public String path = getDefaultPath();
    public int maxSizeMb = 5;
    public int maxHistory = 1;

    private static String getDefaultPath() {
      if (!DiagnosticsHelper.isRpIntegration()) {
        // this will be relative to the directory where agent jar is located
        return DEFAULT_NAME;
      }
      if (DiagnosticsHelper.useAppSvcRpIntegrationLogging()) {
        return StatusFile.getLogDir() + "/" + DEFAULT_NAME;
      }
      if (DiagnosticsHelper.useFunctionsRpIntegrationLogging()
          && !DiagnosticsHelper.isOsWindows()) {
        return "/var/log/applicationinsights/" + DEFAULT_NAME;
      }
      // azure spring cloud
      return DEFAULT_NAME;
    }
  }

  public static class SamplingOverride {
    // TODO (trask) consider making this required when moving out of preview
    @Nullable public SpanKind spanKind;
    // not using include/exclude, because you can still get exclude with this by adding a second
    // (exclude) override above it
    // (since only the first matching override is used)
    public List<SamplingOverrideAttribute> attributes = new ArrayList<>();
    public Float percentage;
    public String id; // optional, used for debugging purposes only

    public void validate() {
      if (spanKind == null && attributes.isEmpty()) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A sampling override configuration is missing \"spanKind\" and has no attributes.",
            "Please provide at least one of \"spanKind\" or \"attributes\" for the sampling override configuration.");
      }
      if (percentage == null) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A sampling override configuration is missing a \"percentage\".",
            "Please provide a \"percentage\" for the sampling override configuration.");
      }
      if (percentage < 0 || percentage > 100) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A sampling override configuration has a \"percentage\" that is not between 0 and 100.",
            "Please provide a \"percentage\" that is between 0 and 100 for the sampling override configuration.");
      }
      for (SamplingOverrideAttribute attribute : attributes) {
        attribute.validate();
      }
    }
  }

  public static class SamplingOverrideAttribute {
    public String key;
    @Nullable public String value;
    @Nullable public MatchType matchType;

    private void validate() {
      if (isEmpty(key)) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A sampling override configuration has an attribute section that is missing a \"key\".",
            "Please provide a \"key\" under the attribute section of the sampling override configuration.");
      }
      if (matchType == null && value != null) {
        throw new FriendlyException(
            "A sampling override configuration has an attribute section with a \"value\" that is missing a \"matchType\".",
            "Please provide a \"matchType\" under the attribute section of the sampling override configuration.");
      }
      if (matchType == MatchType.REGEXP) {
        if (isEmpty(value)) {
          // TODO add doc and go link, similar to telemetry processors
          throw new FriendlyException(
              "Asampling override configuration has an attribute with matchType regexp that is missing a \"value\".",
              "Please provide a key under the attribute section of the sampling override configuration.");
        }
        validateRegex(value);
      }
    }

    private static void validateRegex(String value) {
      try {
        Pattern.compile(value);
      } catch (PatternSyntaxException e) {
        // TODO add doc and go link, similar to telemetry processors
        throw new FriendlyException(
            "A telemetry filter configuration has an invalid regex: " + value,
            "Please provide a valid regex in the telemetry filter configuration.",
            e);
      }
    }
  }

  public static class ProcessorConfig {
    public ProcessorType type;
    public ProcessorIncludeExclude include;
    public ProcessorIncludeExclude exclude;
    public List<ProcessorAction> actions =
        new ArrayList<>(); // specific for processor type "attributes"
    public NameConfig name; // specific for processor type "span"
    public NameConfig body; // specific for processor types "log"
    public String id; // optional, used for debugging purposes only

    public void validate() {
      if (type == null) {
        throw new FriendlyException(
            "A telemetry processor configuration is missing a \"type\".",
            "Please provide a \"type\" in the telemetry processor configuration. "
                + "Learn more about telemetry processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (include != null) {
        include.validate(type, IncludeExclude.INCLUDE);
      }
      if (exclude != null) {
        exclude.validate(type, IncludeExclude.EXCLUDE);
      }
      switch (type) {
        case ATTRIBUTE:
          validateAttributeProcessorConfig();
          return;
        case SPAN:
          validateSpanProcessorConfig();
          return;
        case LOG:
          validateLogProcessorConfig();
          return;
        case METRIC_FILTER:
          validateMetricFilterProcessorConfig();
          return;
      }
      throw new AssertionError("Unexpected processor type: " + type);
    }

    public void validateAttributeProcessorConfig() {
      if (actions.isEmpty()) {
        throw new FriendlyException(
            "An attribute processor configuration has no actions.",
            "Please provide at least one action in the attribute processor configuration. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      for (ProcessorAction action : actions) {
        action.validate();
      }

      validateSectionIsNull(name, "name");
      validateSectionIsNull(body, "body");
    }

    public void validateSpanProcessorConfig() {
      if (name == null) {
        throw new FriendlyException(
            "a span processor configuration is missing a \"name\" section.",
            "Please provide a \"name\" section in the span processor configuration. "
                + "Learn more about span processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      name.validate(type);

      validateActionsIsEmpty();
      validateSectionIsNull(body, "body");
    }

    public void validateLogProcessorConfig() {
      if (body == null) {
        throw new FriendlyException(
            "a log processor configuration is missing a \"body\" section.",
            "Please provide a \"body\" section in the log processor configuration. "
                + "Learn more about log processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      body.validate(type);

      validateActionsIsEmpty();
      validateSectionIsNull(name, "name");
    }

    public void validateMetricFilterProcessorConfig() {
      if (exclude == null) {
        throw new FriendlyException(
            "a metric-filter processor configuration is missing an \"exclude\" section.",
            "Please provide a \"exclude\" section in the metric-filter processor configuration. "
                + "Learn more about metric-filter processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }

      validateActionsIsEmpty();
      validateSectionIsNull(name, "name");
      validateSectionIsNull(body, "body");
    }

    private void validateActionsIsEmpty() {
      if (!actions.isEmpty()) {
        throwUnexpectedSectionFriendlyException("actions");
      }
    }

    private void validateSectionIsNull(Object section, String sectionName) {
      if (section != null) {
        throwUnexpectedSectionFriendlyException(sectionName);
      }
    }

    private void throwUnexpectedSectionFriendlyException(String sectionName) {
      throw new FriendlyException(
          type.anX + " processor configuration has an unexpected section \"" + sectionName + "\".",
          "Please do not provide a \""
              + sectionName
              + "\" section in the "
              + type
              + " processor configuration. "
              + "Learn more about "
              + type
              + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
    }
  }

  public static class NameConfig {
    public List<String> fromAttributes = new ArrayList<>();
    public ToAttributeConfig toAttributes;
    public String separator;

    public void validate(ProcessorType processorType) {
      if (fromAttributes.isEmpty() && toAttributes == null) {
        // TODO different links for different processor types?
        throw new FriendlyException(
            processorType.anX
                + " processor configuration has \"name\" action with no \"fromAttributes\" and no \"toAttributes\".",
            "Please provide at least one of \"fromAttributes\" or \"toAttributes\" under the name section of the "
                + processorType
                + " processor configuration. "
                + "Learn more about "
                + processorType
                + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (toAttributes != null) {
        toAttributes.validate(processorType);
      }
    }
  }

  public static class ToAttributeConfig {
    public List<String> rules = new ArrayList<>();

    public void validate(ProcessorType processorType) {
      if (rules.isEmpty()) {
        throw new FriendlyException(
            processorType.anX
                + " processor configuration has \"toAttributes\" section with no \"rules\".",
            "Please provide at least one rule under the \"toAttributes\" section of the "
                + processorType
                + " processor configuration. "
                + "Learn more about "
                + processorType
                + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      for (String rule : rules) {
        validateRegex(rule, processorType);
      }
    }
  }

  public static class ProcessorIncludeExclude {
    public MatchType matchType;
    public List<String> spanNames = new ArrayList<>();
    public List<String> metricNames = new ArrayList<>();
    public List<ProcessorAttribute> attributes = new ArrayList<>();

    public void validate(ProcessorType processorType, IncludeExclude includeExclude) {
      if (matchType == null) {
        throw new FriendlyException(
            processorType.anX
                + " processor configuration has an "
                + includeExclude
                + " section that is missing a \"matchType\".",
            "Please provide a \"matchType\" under the "
                + includeExclude
                + " section of the "
                + processorType
                + " processor configuration. "
                + "Learn more about "
                + processorType
                + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      for (ProcessorAttribute attribute : attributes) {
        if (isEmpty(attribute.key)) {
          throw new FriendlyException(
              processorType.anX
                  + " processor configuration has an "
                  + includeExclude
                  + " section that is missing a \"key\".",
              "Please provide a \"key\" under the "
                  + includeExclude
                  + " section of the "
                  + processorType
                  + " processor configuration. "
                  + "Learn more about "
                  + processorType
                  + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (matchType == MatchType.REGEXP && attribute.value != null) {
          validateRegex(attribute.value, processorType);
        }
      }

      switch (processorType) {
        case ATTRIBUTE:
          validAttributeProcessorIncludeExclude(includeExclude);
          return;
        case LOG:
          validateLogProcessorIncludeExclude(includeExclude);
          return;
        case SPAN:
          validateSpanProcessorIncludeExclude(includeExclude);
          return;
        case METRIC_FILTER:
          validateMetricFilterProcessorExclude(includeExclude);
          return;
      }
      throw new IllegalStateException("Unexpected processor type: " + processorType);
    }

    private void validAttributeProcessorIncludeExclude(IncludeExclude includeExclude) {
      if (attributes.isEmpty() && spanNames.isEmpty()) {
        throw new FriendlyException(
            "An attribute processor configuration has an "
                + includeExclude
                + " section with no \"spanNames\" and no \"attributes\".",
            "Please provide at least one of \"spanNames\" or \"attributes\" under the "
                + includeExclude
                + " section of the attribute processor configuration. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (matchType == MatchType.REGEXP) {
        for (String spanName : spanNames) {
          validateRegex(spanName, ProcessorType.ATTRIBUTE);
        }
      }

      validateSectionIsEmpty(metricNames, ProcessorType.ATTRIBUTE, includeExclude, "metricNames");
    }

    private void validateLogProcessorIncludeExclude(IncludeExclude includeExclude) {
      if (attributes.isEmpty()) {
        throw new FriendlyException(
            "A log processor configuration has an "
                + includeExclude
                + " section with no \"attributes\".",
            "Please provide \"attributes\" under the "
                + includeExclude
                + " section of the log processor configuration. "
                + "Learn more about log processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }

      validateSectionIsEmpty(spanNames, ProcessorType.LOG, includeExclude, "spanNames");
      validateSectionIsEmpty(metricNames, ProcessorType.LOG, includeExclude, "metricNames");
    }

    private void validateSpanProcessorIncludeExclude(IncludeExclude includeExclude) {
      if (spanNames.isEmpty() && attributes.isEmpty()) {
        throw new FriendlyException(
            "A span processor configuration has "
                + includeExclude
                + " section with no \"spanNames\" and no \"attributes\".",
            "Please provide at least one of \"spanNames\" or \"attributes\" under the "
                + includeExclude
                + " section of the span processor configuration. "
                + "Learn more about span processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (matchType == MatchType.REGEXP) {
        for (String spanName : spanNames) {
          validateRegex(spanName, ProcessorType.SPAN);
        }
      }

      validateSectionIsEmpty(metricNames, ProcessorType.SPAN, includeExclude, "metricNames");
    }

    private void validateMetricFilterProcessorExclude(IncludeExclude includeExclude) {
      if (includeExclude == IncludeExclude.INCLUDE) {
        throw new FriendlyException(
            "A metric-filter processor configuration has an include section.",
            "Please do not provide an \"include\" section in the metric-filter processor configuration. "
                + "Learn more about span processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (metricNames.isEmpty()) {
        throw new FriendlyException(
            "A metric-filter processor configuration has an exclude section with no \"metricNames\".",
            "Please provide a \"metricNames\" section under the exclude section of the metric-filter processor configuration. "
                + "Learn more about span processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (matchType == MatchType.REGEXP) {
        for (String metricName : metricNames) {
          validateRegex(metricName, ProcessorType.METRIC_FILTER);
        }
      }

      validateSectionIsEmpty(
          spanNames, ProcessorType.METRIC_FILTER, IncludeExclude.EXCLUDE, "spanNames");
    }

    private static void validateSectionIsEmpty(
        List<?> list, ProcessorType type, IncludeExclude includeExclude, String sectionName) {
      if (!list.isEmpty()) {
        throwUnexpectedSectionFriendlyException(type, includeExclude, sectionName);
      }
    }

    private static void throwUnexpectedSectionFriendlyException(
        ProcessorType type, IncludeExclude includeExclude, String sectionName) {
      throw new FriendlyException(
          type.anX
              + " processor configuration has "
              + includeExclude
              + " section with an unexpected section \""
              + sectionName
              + "\".",
          "Please do not provide a \""
              + sectionName
              + "\" section under the "
              + includeExclude
              + " section of the "
              + type
              + " processor configuration. "
              + "Learn more about "
              + type
              + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
    }
  }

  private static void validateRegex(String value, ProcessorType processorType) {
    try {
      Pattern.compile(value);
    } catch (PatternSyntaxException e) {
      // TODO different links for different processor types throughout?
      throw new FriendlyException(
          processorType.anX + " processor configuration has an invalid regex:" + value,
          "Please provide a valid regex in the "
              + processorType
              + " processor configuration. "
              + "Learn more about "
              + processorType
              + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557",
          e);
    }
  }

  public static class ProcessorAttribute {
    public String key;
    public String value;
  }

  public static class ExtractAttribute {

    public final Pattern pattern;
    public final List<String> groupNames;

    // visible for testing
    public ExtractAttribute(Pattern pattern, List<String> groupNames) {
      this.pattern = pattern;
      this.groupNames = groupNames;
    }

    // TODO: Handle empty patterns or groupNames are not populated gracefully
    public void validate() {
      if (groupNames.isEmpty()) {
        throw new FriendlyException(
            "An attribute processor configuration does not have valid regex to extract attributes: "
                + pattern,
            "Please provide a valid regex of the form (?<name>X) where X is the usual regular expression. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
    }
  }

  public static class MaskAttribute {
    private static final Pattern replacePattern = Pattern.compile("\\$\\{[A-Za-z1-9]*\\}*");
    public final Pattern pattern;
    public final List<String> groupNames;
    public final String replace;

    // visible for testing
    public MaskAttribute(Pattern pattern, List<String> groupNames, String replace) {
      this.pattern = pattern;
      this.groupNames = groupNames;
      this.replace = replace;
    }

    // TODO: Handle empty patterns or groupNames are not populated gracefully
    public void validate() {
      if (groupNames.isEmpty()) {
        throw new FriendlyException(
            "An attribute processor configuration does not have valid regex to mask attributes: "
                + pattern,
            "Please provide a valid regex of the form (?<name>X) where X is the usual regular expression. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }

      Matcher maskMatcher = replacePattern.matcher(replace);
      while (maskMatcher.find()) {
        String groupName = maskMatcher.group();
        String replacedString = "";
        if (groupName.length() > 3) {
          // to extract string of format ${foo}
          replacedString = groupName.substring(2, groupName.length() - 1);
        }
        if (replacedString.isEmpty()) {
          throw new FriendlyException(
              "An attribute processor configuration does not have valid `replace` value to mask attributes: "
                  + replace,
              "Please provide a valid replace value of the form (${foo}***${bar}). "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (!groupNames.contains(replacedString)) {
          throw new FriendlyException(
              "An attribute processor configuration does not have valid `replace` value to mask attributes: "
                  + replace,
              "Please make sure the replace value matches group names used in the `pattern` regex. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
      }
    }
  }

  public static class ProcessorAction {
    public final AttributeKey<String> key;
    public final ProcessorActionType action;
    public final String value;
    public final AttributeKey<String> fromAttribute;
    public final ExtractAttribute extractAttribute;
    public final MaskAttribute maskAttribute;

    @JsonCreator
    public ProcessorAction(
        // TODO (trask) should this take attribute type, e.g. "key:type"
        @JsonProperty("key") String key,
        @JsonProperty("action") ProcessorActionType action,
        @JsonProperty("value") String value,
        // TODO (trask) should this take attribute type, e.g. "key:type"
        @JsonProperty("fromAttribute") String fromAttribute,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("replace") String replace) {
      this.key = isEmpty(key) ? null : AttributeKey.stringKey(key);
      this.action = action;
      this.value = value;
      this.fromAttribute = isEmpty(fromAttribute) ? null : AttributeKey.stringKey(fromAttribute);

      if (pattern == null) {
        extractAttribute = null;
        maskAttribute = null;
      } else {
        Pattern regexPattern;
        try {
          regexPattern = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
          throw new FriendlyException(
              "Telemetry processor configuration does not have valid regex:" + pattern,
              "Please provide a valid regex in the telemetry processors configuration. "
                  + "Learn more about telemetry processors here: https://go.microsoft.com/fwlink/?linkid=2151557",
              e);
        }
        List<String> groupNames = Patterns.getGroupNames(pattern);
        if (replace != null) {
          extractAttribute = null;
          maskAttribute = new Configuration.MaskAttribute(regexPattern, groupNames, replace);
        } else {
          maskAttribute = null;
          extractAttribute = new Configuration.ExtractAttribute(regexPattern, groupNames);
        }
      }
    }

    public void validate() {

      if (key == null) {
        throw new FriendlyException(
            "An attribute processor configuration has an action section that is missing a \"key\".",
            "Please provide a \"key\" under the action section of the attribute processor configuration. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (action == null) {
        throw new FriendlyException(
            "An attribute processor configuration has an action section that is missing an \"action\".",
            "Please provide an \"action\" under the action section of the attribute processor configuration. "
                + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
      }
      if (action == ProcessorActionType.INSERT || action == ProcessorActionType.UPDATE) {
        if (isEmpty(value) && fromAttribute == null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action that is missing a \"value\" or a \"fromAttribute\".",
              "Please provide exactly one of \"value\" or \"fromAttributes\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (!isEmpty(value) && fromAttribute != null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action that has both a \"value\" and a \"fromAttribute\".",
              "Please provide exactly one of \"value\" or \"fromAttributes\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (extractAttribute != null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action with an \"pattern\" section.",
              "Please do not provide an \"pattern\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (maskAttribute != null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action with an \"replace\" section.",
              "Please do not provide an \"replace\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
      }

      if (action == ProcessorActionType.EXTRACT) {
        if (extractAttribute == null) {
          throw new FriendlyException(
              "An attribute processor configuration has an extract action that is missing an \"pattern\" section.",
              "Please provide an \"pattern\" section under the extract action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (!isEmpty(value)) {
          throw new FriendlyException(
              "An attribute processor configuration has an " + action + " action with a \"value\".",
              "Please do not provide a \"value\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (fromAttribute != null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action with a \"fromAttribute\".",
              "Please do not provide a \"fromAttribute\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        extractAttribute.validate();
      }

      if (action == ProcessorActionType.MASK) {
        if (maskAttribute == null) {
          throw new FriendlyException(
              "An attribute processor configuration has an mask action that is missing an \"pattern\" or \"replace\" section.",
              "Please provide an \"pattern\" section and \"replace\" section under the mask action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (!isEmpty(value)) {
          throw new FriendlyException(
              "An attribute processor configuration has an " + action + " action with a \"value\".",
              "Please do not provide a \"value\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        if (fromAttribute != null) {
          throw new FriendlyException(
              "An attribute processor configuration has an "
                  + action
                  + " action with a \"fromAttribute\".",
              "Please do not provide a \"fromAttribute\" under the "
                  + action
                  + " action. "
                  + "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
        }
        maskAttribute.validate();
      }
    }
  }

  public static class ProfilerConfiguration {
    public int configPollPeriodSeconds = 60;
    public int periodicRecordingDurationSeconds = 120;
    public int periodicRecordingIntervalSeconds = 60 * 60;
    public boolean enabled = false;
    public String memoryTriggeredSettings = "profile";
    public String cpuTriggeredSettings = "profile";
  }

  public static class GcEventConfiguration {
    public GcReportingLevel reportingLevel = GcReportingLevel.TENURED_ONLY;
  }

  public static class AadAuthentication {
    public boolean enabled;
    public AuthenticationType type;
    public String clientId;
    public String tenantId;
    public String clientSecret;
    public String authorityHost;

    public void validate() {
      if (!enabled) {
        return;
      }
      if (type == null) {
        throw new FriendlyException(
            "AAD Authentication configuration is missing authentication \"type\".",
            "Please provide a valid authentication \"type\" under the \"authentication\" configuration. "
                + "Learn more about authentication configuration here: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config");
      }

      if (type == AuthenticationType.UAMI) {
        if (isEmpty(clientId)) {
          throw new FriendlyException(
              "AAD Authentication configuration of type User Assigned Managed Identity is missing \"clientId\".",
              "Please provide a valid \"clientId\" under the \"authentication\" configuration. "
                  + "Learn more about authentication configuration here: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config");
        }
      }

      if (type == AuthenticationType.CLIENTSECRET) {
        if (isEmpty(clientId)) {
          throw new FriendlyException(
              "AAD Authentication configuration of type Client Secret Identity is missing \"clientId\".",
              "Please provide a valid \"clientId\" under the \"authentication\" configuration. "
                  + "Learn more about authentication configuration here: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config");
        }

        if (isEmpty(tenantId)) {
          throw new FriendlyException(
              "AAD Authentication configuration of type Client Secret Identity is missing \"tenantId\".",
              "Please provide a valid \"tenantId\" under the \"authentication\" configuration. "
                  + "Learn more about authentication configuration here: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config");
        }

        if (isEmpty(clientSecret)) {
          throw new FriendlyException(
              "AAD Authentication configuration of type Client Secret Identity is missing \"clientSecret\".",
              "Please provide a valid \"clientSecret\" under the \"authentication\" configuration. "
                  + "Learn more about authentication configuration here: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config");
        }
      }
    }
  }

  public enum AuthenticationType {
    // TODO (kyralama) should these use @JsonProperty to bind lowercase like other enums?
    UAMI,
    SAMI,
    VSCODE,
    CLIENTSECRET
  }
}
