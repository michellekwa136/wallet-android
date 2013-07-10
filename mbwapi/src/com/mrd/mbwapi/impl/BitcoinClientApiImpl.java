/*
 * Copyright 2013 Megion Research and Development GmbH
 *
 *  Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 *  This license governs use of the accompanying software. If you use the software, you accept this license.
 *  If you do not accept the license, do not use the software.
 *
 *  1. Definitions
 *  The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 *  "You" means the licensee of the software.
 *  "Your company" means the company you worked for when you downloaded the software.
 *  "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 *  of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 *  software, and specifically excludes the right to distribute the software outside of your company.
 *  "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 *  under this license.
 *
 *  2. Grant of Rights
 *  (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 *  worldwide, royalty-free copyright license to reproduce the software for reference use.
 *  (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 *  worldwide, royalty-free patent license under licensed patents for reference use.
 *
 *  3. Limitations
 *  (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 *  (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 *  (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 *  (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 *  guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 *  change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 *  fitness for a particular purpose and non-infringement.
 *
 */

package com.mrd.mbwapi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.util.ByteReader;
import com.mrd.bitlib.util.ByteWriter;
import com.mrd.mbwapi.api.ApiException;
import com.mrd.mbwapi.api.ApiObject;
import com.mrd.mbwapi.api.BitcoinClientApi;
import com.mrd.mbwapi.api.BroadcastTransactionRequest;
import com.mrd.mbwapi.api.BroadcastTransactionResponse;
import com.mrd.mbwapi.api.QueryBalanceRequest;
import com.mrd.mbwapi.api.QueryBalanceResponse;
import com.mrd.mbwapi.api.QueryExchangeSummaryRequest;
import com.mrd.mbwapi.api.QueryExchangeSummaryResponse;
import com.mrd.mbwapi.api.QueryTransactionInventoryRequest;
import com.mrd.mbwapi.api.QueryTransactionInventoryResponse;
import com.mrd.mbwapi.api.QueryTransactionSummaryRequest;
import com.mrd.mbwapi.api.QueryTransactionSummaryResponse;
import com.mrd.mbwapi.api.QueryUnspentOutputsRequest;
import com.mrd.mbwapi.api.QueryUnspentOutputsResponse;
import com.mrd.mbwapi.util.SslUtils;

public class BitcoinClientApiImpl implements BitcoinClientApi {

   public static class HttpEndpoint {
      public String baseUrlString;

      public HttpEndpoint(String baseUrlString) {
         this.baseUrlString = baseUrlString;
      }
   }

   public static class HttpsEndpoint extends HttpEndpoint {
      String certificateThumbprint;

      public HttpsEndpoint(String baseUrlString, String certificateThumbprint) {
         super(baseUrlString);
         this.certificateThumbprint = certificateThumbprint;
      }
   }

   private static final String API_PREFIX = "/api/1/request/";
   private HttpEndpoint[] _serverEndpoints;
   private int _currentServerUrlIndex;
   private NetworkParameters _network;

   public BitcoinClientApiImpl(HttpEndpoint[] serverEndpoints, NetworkParameters network) {
      // Prepare raw URL strings with prefix
      _serverEndpoints = serverEndpoints;
      // Choose a random URL to use
      _currentServerUrlIndex = new Random().nextInt(_serverEndpoints.length);
      _network = network;
   }

   @Override
   public NetworkParameters getNetwork() {
      return _network;
   }

   @Override
   public QueryBalanceResponse queryBalance(QueryBalanceRequest request) throws ApiException {
      HttpURLConnection connection = sendRequest(request, "queryBalance");
      return receiveResponse(QueryBalanceResponse.class, connection);
   }

   @Override
   public QueryExchangeSummaryResponse queryExchangeSummary(QueryExchangeSummaryRequest request) throws ApiException {
      HttpURLConnection connection = sendRequest(request, "queryExchangeSummary");
      return receiveResponse(QueryExchangeSummaryResponse.class, connection);
   }

   @Override
   public QueryUnspentOutputsResponse queryUnspentOutputs(QueryUnspentOutputsRequest request) throws ApiException {
      HttpURLConnection connection = sendRequest(request, "queryUnspentOutputs");
      return receiveResponse(QueryUnspentOutputsResponse.class, connection);
   }

   @Override
   public BroadcastTransactionResponse broadcastTransaction(BroadcastTransactionRequest request) throws ApiException {
      HttpURLConnection connection = sendRequest(request, "broadcastTransaction");
      return receiveResponse(BroadcastTransactionResponse.class, connection);
   }

   @Override
   public QueryTransactionInventoryResponse queryTransactionInventory(QueryTransactionInventoryRequest request)
         throws ApiException {
      HttpURLConnection connection = sendRequest(request, "queryTransactionInventory");
      return receiveResponse(QueryTransactionInventoryResponse.class, connection);
   }

   @Override
   public QueryTransactionSummaryResponse queryTransactionSummary(QueryTransactionSummaryRequest request)
         throws ApiException {
      HttpURLConnection connection = sendRequest(request, "queryTransactionSummary");
      return receiveResponse(QueryTransactionSummaryResponse.class, connection);
   }

   private HttpURLConnection sendRequest(ApiObject request, String function) throws ApiException {
      try {
         HttpURLConnection connection = getConnectionAndSendRequest(request, function);
         if (connection == null) {
            throw new ApiException(BitcoinClientApi.ERROR_CODE_COMMUNICATION_ERROR, "Unable to connect to the server");
         }
         int status = connection.getResponseCode();
         if (status != 200) {
            throw new ApiException(BitcoinClientApi.ERROR_CODE_UNEXPECTED_SERVER_RESPONSE, "Unexpected status code: "
                  + status);
         }
         int contentLength = connection.getContentLength();
         if (contentLength == -1) {
            throw new ApiException(BitcoinClientApi.ERROR_CODE_UNEXPECTED_SERVER_RESPONSE, "Invalid content-length");
         }
         return connection;
      } catch (IOException e) {
         throw new ApiException(BitcoinClientApi.ERROR_CODE_COMMUNICATION_ERROR, e.getMessage());
      }
   }

   /**
    * Attempt to connect and send to a URL in our list of URLS, if it fails try
    * the next until we have cycled through all URLs
    */
   private HttpURLConnection getConnectionAndSendRequest(ApiObject request, String function) {
      int originalConnectionIndex = _currentServerUrlIndex;
      while (true) {
         try {
            HttpURLConnection connection = getHttpConnection(_serverEndpoints[_currentServerUrlIndex], function);
            byte[] toSend = request.serialize(new ByteWriter(1024)).toBytes();
            connection.setRequestProperty("Content-Length", String.valueOf(toSend.length));
            connection.getOutputStream().write(toSend);
            return connection;
         } catch (IOException e) {
            _currentServerUrlIndex = (_currentServerUrlIndex + 1) % _serverEndpoints.length;
            if (_currentServerUrlIndex == originalConnectionIndex) {
               // We have tried all URLs
               return null;
            }
         }
      }
   }

   private <T> T receiveResponse(Class<T> klass, HttpURLConnection connection) throws ApiException {
      try {
         int contentLength = connection.getContentLength();
         byte[] received = readBytes(contentLength, connection.getInputStream());
         T response = ApiObject.deserialize(klass, new ByteReader(received));
         return response;
      } catch (IOException e) {
         throw new ApiException(BitcoinClientApi.ERROR_CODE_COMMUNICATION_ERROR, e.getMessage());
      }
   }

   private byte[] readBytes(int size, InputStream inputStream) throws IOException {
      byte[] bytes = new byte[size];
      int index = 0;
      int toRead;
      while ((toRead = size - index) > 0) {
         int read = inputStream.read(bytes, index, toRead);
         if (read == -1) {
            throw new IOException();
         }
         index += read;
      }
      return bytes;
   }

   private HttpURLConnection getHttpConnection(HttpEndpoint serverEndpoint, String function) throws IOException {
      StringBuilder sb = new StringBuilder();
      String spec = sb.append(serverEndpoint.baseUrlString).append(API_PREFIX).append(function).toString();
      URL url = new URL(spec);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      if (serverEndpoint instanceof HttpsEndpoint) {
         SslUtils.configureTrustedCertificate(connection, ((HttpsEndpoint) serverEndpoint).certificateThumbprint);
      }
      connection.setReadTimeout(60000);
      connection.setDoInput(true);
      connection.setDoOutput(true);
      return connection;
   }
}