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

package com.microsoft.applicationinsights.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.MessageData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import com.microsoft.applicationinsights.smoketest.schemav2.SeverityLevel;
import java.util.List;
import org.junit.Test;

@UseAgent("inheritedattributes")
public class SpringBootAutoTest extends AiSmokeTest {

  @Test
  @TargetUri("/test")
  public void test() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> mdList = mockedIngestion.waitForMessageItemsInRequest(1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope mdEnvelope = mdList.get(0);
    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    MessageData md = (MessageData) ((Data<?>) mdEnvelope.getData()).getBaseData();

    assertEquals("GET /test", rd.getName());
    assertEquals("200", rd.getResponseCode());
    assertEquals("z", rd.getProperties().get("tenant"));
    assertEquals(1, rd.getProperties().size());
    assertTrue(rd.getSuccess());

    assertEquals("hello", md.getMessage());
    assertEquals(SeverityLevel.Information, md.getSeverityLevel());
    assertEquals("Logger", md.getProperties().get("SourceType"));
    assertEquals("INFO", md.getProperties().get("LoggingLevel"));
    assertEquals("smoketestapp", md.getProperties().get("LoggerName"));
    assertNotNull(md.getProperties().get("ThreadName"));
    assertEquals("z", rd.getProperties().get("tenant"));
    assertEquals(5, md.getProperties().size());
  }
}
