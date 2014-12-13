/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.util;

import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.NanoClock;
import com.google.api.client.util.Sleeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Implements a request initializer which adds retry handlers to all
 * HttpRequests.
 *
 * This allows chaining through to another HttpRequestInitializer, since
 * clients have exactly one HttpRequestInitializer, and Credential is also
 * a required HttpRequestInitializer.
 */
public class RetryHttpRequestInitializer implements HttpRequestInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(RetryHttpRequestInitializer.class);

  /**
   * Http response codes that should be silently ignored.
   */
  private static final Set<Integer> IGNORED_RESPONSE_CODES = new HashSet<>(
      Arrays.asList(307 /* Redirect, handled by Apiary client */,
                    308 /* Resume Incomplete, handled by Apiary client */));

  /**
   * Http response timeout to use for hanging gets.
   */
  private static final int HANGING_GET_TIMEOUT_SEC = 80;

  private static class LoggingHttpBackOffIOExceptionHandler
      extends HttpBackOffIOExceptionHandler {
    public LoggingHttpBackOffIOExceptionHandler(BackOff backOff) {
      super(backOff);
    }

    @Override
    public boolean handleIOException(HttpRequest request, boolean supportsRetry)
        throws IOException {
      boolean willRetry = super.handleIOException(request, supportsRetry);
      if (willRetry) {
        LOG.info("Request failed with IOException, will retry: {}", request.getUrl());
      } else {
        LOG.info("Request failed with IOException, will NOT retry: {}", request.getUrl());
      }
      return willRetry;
    }
  }

  private static class LoggingHttpBackoffUnsuccessfulResponseHandler
      implements HttpUnsuccessfulResponseHandler {
    private final HttpBackOffUnsuccessfulResponseHandler handler;

    public LoggingHttpBackoffUnsuccessfulResponseHandler(BackOff backoff,
        Sleeper sleeper) {
      handler = new HttpBackOffUnsuccessfulResponseHandler(backoff);
      handler.setSleeper(sleeper);
      handler.setBackOffRequired(
          new HttpBackOffUnsuccessfulResponseHandler.BackOffRequired() {
            @Override
            public boolean isRequired(HttpResponse response) {
              int statusCode = response.getStatusCode();
              return (statusCode / 100 == 5) ||  // 5xx: server error
                  statusCode == 429;             // 429: Too many requests
            }
          });
    }

    @Override
    public boolean handleResponse(HttpRequest request, HttpResponse response,
        boolean supportsRetry) throws IOException {
      boolean retry = handler.handleResponse(request, response, supportsRetry);
      if (retry) {
        LOG.info("Request failed with code {} will retry: {}",
            response.getStatusCode(), request.getUrl());

      } else if (!IGNORED_RESPONSE_CODES.contains(response.getStatusCode())) {
        LOG.info("Request failed with code {}, will NOT retry: {}",
            response.getStatusCode(), request.getUrl());
      }

      return retry;
    }
  }

  private final HttpRequestInitializer chained;

  private final NanoClock nanoClock;  // used for testing

  private final Sleeper sleeper;  // used for testing

  /**
   * @param chained a downstream HttpRequestInitializer, which will also be
   *                applied to HttpRequest initialization.  May be null.
   */
  public RetryHttpRequestInitializer(@Nullable HttpRequestInitializer chained) {
    this(chained, NanoClock.SYSTEM, Sleeper.DEFAULT);
  }

  public RetryHttpRequestInitializer(@Nullable HttpRequestInitializer chained,
      NanoClock nanoClock, Sleeper sleeper) {
    this.chained = chained;
    this.nanoClock = nanoClock;
    this.sleeper = sleeper;
  }

  @Override
  public void initialize(HttpRequest request) throws IOException {
    if (chained != null) {
      chained.initialize(request);
    }

    // Set a timeout for hanging-gets.
    // TODO: Do this exclusively for work requests.
    request.setReadTimeout(HANGING_GET_TIMEOUT_SEC * 1000);

    // Back off on retryable http errors.
    request.setUnsuccessfulResponseHandler(
        // A back-off multiplier of 2 raises the maximum request retrying time
        // to approximately 5 minutes (keeping other back-off parameters to
        // their default values).
        new LoggingHttpBackoffUnsuccessfulResponseHandler(
            new ExponentialBackOff.Builder().setNanoClock(nanoClock)
                                            .setMultiplier(2).build(),
            sleeper));

    // Retry immediately on IOExceptions.
    LoggingHttpBackOffIOExceptionHandler loggingBackoffHandler =
        new LoggingHttpBackOffIOExceptionHandler(BackOff.ZERO_BACKOFF);
    request.setIOExceptionHandler(loggingBackoffHandler);
  }
}
