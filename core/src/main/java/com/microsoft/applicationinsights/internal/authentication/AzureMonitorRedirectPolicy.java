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

package com.microsoft.applicationinsights.internal.authentication;

import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.HttpURLConnection;

// This is a copy from Azure Monitor Open Telemetry Exporter SDK AzureMonitorRedirectPolicy
public final class AzureMonitorRedirectPolicy implements HttpPipelinePolicy {

    private static final int PERMANENT_REDIRECT_STATUS_CODE = 308;
    private static final int TEMP_REDIRECT_STATUS_CODE = 307;
    // Based on Stamp specific redirects design doc
    private static final int MAX_REDIRECT_RETRIES = 10;
    private static final Logger logger = LoggerFactory.getLogger(AzureMonitorRedirectPolicy.class);
    private volatile String redirectedEndpointUrl;

    @Override
    public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
        return attemptRetry(context, next, context.getHttpRequest(), 0);
    }

    /**
     *  Function to process through the HTTP Response received in the pipeline
     *  and retry sending the request with new redirect url.
     */
    private Mono<HttpResponse> attemptRetry(HttpPipelineCallContext context,
                                            HttpPipelineNextPolicy next,
                                            HttpRequest originalHttpRequest,
                                            int retryCount) {
        // make sure the context is not modified during retry, except for the URL
        context.setHttpRequest(originalHttpRequest.copy());
        if (this.redirectedEndpointUrl != null) {
            context.getHttpRequest().setUrl(this.redirectedEndpointUrl);
        }
        return next.clone().process()
                .flatMap(httpResponse -> {
                    if (shouldRetryWithRedirect(httpResponse.getStatusCode(), retryCount)) {
                        String responseLocation = httpResponse.getHeaderValue("Location");
                        if (responseLocation != null) {
                            this.redirectedEndpointUrl = responseLocation;
                            return attemptRetry(context, next, originalHttpRequest, retryCount + 1);
                        }
                    }
                    return Mono.just(httpResponse);
                });
    }

    /**
     * Determines if it's a valid retry scenario based on statusCode and tryCount.
     *
     * @param statusCode HTTP response status code
     * @param tryCount Redirect retries so far
     * @return True if statusCode corresponds to HTTP redirect response codes and redirect
     * retries is less than {@code MAX_REDIRECT_RETRIES}.
     */
    private static boolean shouldRetryWithRedirect(int statusCode, int tryCount) {
        if (tryCount >= MAX_REDIRECT_RETRIES) {
            logger.warn("Max redirect retries limit reached:{}.", MAX_REDIRECT_RETRIES);
            return false;
        }
        return statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                || statusCode == HttpURLConnection.HTTP_MOVED_PERM
                || statusCode == PERMANENT_REDIRECT_STATUS_CODE
                || statusCode == TEMP_REDIRECT_STATUS_CODE;
    }
}
