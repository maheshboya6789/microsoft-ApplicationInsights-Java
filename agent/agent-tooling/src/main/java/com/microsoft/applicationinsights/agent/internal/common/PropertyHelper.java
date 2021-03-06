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

package com.microsoft.applicationinsights.agent.internal.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class for reading data from a properties file found on the class path. */
public final class PropertyHelper {

  private static final Logger logger = LoggerFactory.getLogger(PropertyHelper.class);

  public static final String SDK_VERSION_FILE_NAME = "ai.sdk-version.properties";

  public static final String VERSION_STRING_PREFIX = "java:";
  public static final String VERSION_PROPERTY_NAME = "version";

  public static final String UNKNOWN_VERSION_VALUE = "unknown";

  /**
   * Reads the properties from a properties file.
   *
   * @param name of the properties file.
   * @return A {@link Properties} object containing the properties read from the provided file.
   * @throws IOException in case
   */
  private static Properties getProperties(String name) throws IOException {
    Properties props = new Properties();
    ClassLoader classLoader = PropertyHelper.class.getClassLoader();
    if (classLoader == null) {
      classLoader = ClassLoader.getSystemClassLoader();
    }

    // Look in the class loader's default location.
    InputStream inputStream = classLoader.getResourceAsStream(name);
    if (inputStream != null) {
      try {
        props.load(inputStream);
      } finally {
        inputStream.close();
      }
    }

    return props;
  }

  /**
   * A method that loads the properties file that contains SDK-Version data.
   *
   * @return The properties or null if not found.
   */
  private static Properties getSdkVersionProperties() {
    try {
      return getProperties(SDK_VERSION_FILE_NAME);
    } catch (IOException e) {
      logger.error("Could not find sdk version file '{}'", SDK_VERSION_FILE_NAME, e);
      return new Properties();
    }
  }

  /**
   * Returns the SDK version string, the <i>version-number</i> prefixed with "java:". The
   * <i>version-number</i> is the value of the {@value #VERSION_PROPERTY_NAME} property in {@value
   * #SDK_VERSION_FILE_NAME}. If the properties file cannot be read, {@value #UNKNOWN_VERSION_VALUE}
   * is used for the <i>version-number</i>.
   *
   * @return "java:<i>version-number</i>" or "java:unknown"
   */
  public static String getQualifiedSdkVersionString() {
    return SdkPropertyValues.sdkVersionString;
  }

  public static void setSdkNamePrefix(String sdkNamePrefix) {
    SdkPropertyValues.sdkVersionString =
        sdkNamePrefix + VERSION_STRING_PREFIX + SdkPropertyValues.SDK_VERSION_NUMBER;
  }

  public static String getSdkVersionNumber() {
    return SdkPropertyValues.SDK_VERSION_NUMBER;
  }

  private static class SdkPropertyValues {
    private static final String SDK_VERSION_NUMBER;
    private static volatile String sdkVersionString;

    static {
      Properties properties = getSdkVersionProperties();
      SDK_VERSION_NUMBER = properties.getProperty(VERSION_PROPERTY_NAME, UNKNOWN_VERSION_VALUE);
      sdkVersionString = VERSION_STRING_PREFIX + SDK_VERSION_NUMBER;
    }

    private SdkPropertyValues() {}
  }

  private PropertyHelper() {}
}
