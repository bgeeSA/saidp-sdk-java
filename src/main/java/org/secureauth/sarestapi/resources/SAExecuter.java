package org.secureauth.sarestapi.resources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.javafx.collections.MappingChange;
/*
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
*/
import org.secureauth.sarestapi.data.*;
import org.secureauth.sarestapi.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Jersey 2 Libs
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import org.glassfish.jersey.client.ClientConfig;
import javax.ws.rs.core.Response;


/*
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
*/

/**
 * @author rrowcliffe@secureauth.com
 *
Copyright (c) 2015, SecureAuth
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class SAExecuter {

    private ClientConfig config = null;
    private Client client=null;
    private static Logger logger=LoggerFactory.getLogger(SAExecuter.class);
    
    //Set up our Connection
    private void createConnection() throws Exception{

        config = new ClientConfig();

        TrustManager[] certs = new TrustManager[]{
                new X509TrustManager(){
                    @Override
                    public X509Certificate[] getAcceptedIssuers(){
                        return null;
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException{}
                }
        };

        SSLContext ctx = null;

        try{
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, certs, new SecureRandom());
        }catch(java.security.GeneralSecurityException ex){
            logger.error(new StringBuilder().append("Exception occurred while attempting to setup SSL security. ").toString(), ex);
        }

        //logger.error("Setting url connection!");
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());

        try{
             client = ClientBuilder.newBuilder()
                    .sslContext(ctx)
                    .hostnameVerifier(
                            new HostnameVerifier(){
                                @Override
                                public boolean verify(String hostname, SSLSession session){return true;}
                            }
                    )
                    .build();
            /*
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(
                    new HostnameVerifier(){
                        @Override
                        public boolean verify(String hostname, SSLSession session){return true;}
                    },ctx));
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception occurred while attempting to associating our SSL cert to the session.").toString(), e);
        }

        try{
            client = ClientBuilder.newClient(config);
        }catch(Exception e){
            StringBuilder bud = new StringBuilder();
            for(StackTraceElement st: e.getStackTrace()){
                bud.append(st.toString()).append("\n");
            }
            throw new Exception(new StringBuilder().append("Exception occurred while attempting to create connection object. Exception: ")
                    .append(e.getMessage()).append("\nStackTraceElements:\n").append(bud.toString()).toString());
        }

        if(client == null) throw new Exception(new StringBuilder().append("Unable to create connection object, creation attempt returned NULL.").toString());
    }

    //Get Factors for the user requested
    public <T> T executeGetRequest(String auth, String query,String ts,  Class<T> valueType)throws Exception {
        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String factors=null;
        T genericResponse =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    get();
            genericResponse = response.readEntity(valueType);

            //System.out.println(factors);
            /*
            JAXBContext context = JAXBContext.newInstance(valueType);
            context.createUnmarshaller();

            InputStream inStream = new ByteArrayInputStream(factors.getBytes());
            factorsResponse = new ObjectMapper().readValue(inStream, valueType);
*/
        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception getting User Factors: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return genericResponse;

    }

    //Validate User against Repository
    public ResponseObject executeValidateUser(String header,String query, AuthRequest authRequest,String ts)throws Exception{

        if(client == null) {
            createConnection();
        }


        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{
            target = client.target(query);

            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", header).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest),MediaType.APPLICATION_JSON));

            responseObject = response.readEntity(ResponseObject.class);

            /*
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());

            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Validating User: \nQuery:\n\t")
                    .append(query).append("\nError: \n\t").append(responseStr).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate Users Password
    public ResponseObject executeValidateUserPassword(String auth,String query, AuthRequest authRequest,String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));
            responseObject=response.readEntity(ResponseObject.class);
            /*
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Validating User Password: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate Users KBA
    public ResponseObject executeValidateKba(String auth,String query, AuthRequest authRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */
        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Validating KBA: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate User Oath Token
    public ResponseObject executeValidateOath(String auth,String query, AuthRequest authRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request()
                    .accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Validating OATH: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate OTP By Phone
    public ResponseObject executeOTPByPhone(String auth,String query, AuthRequest authRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Delivering OTP by Phone: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate User OATH by SMS
    public ResponseObject executeOTPBySMS(String auth,String query, AuthRequest authRequest,String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request()
                    .accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Delivering OTP by SMS: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate User OTP by Email
    public ResponseObject executeOTPByEmail(String auth,String query, AuthRequest authRequest,String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest), MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Delivering OTP by Email: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

     // post request
    public <T> T executePostRequest(String auth,String query, AuthRequest authRequest,String ts, Class<T> valueType)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        T responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest),MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(valueType);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(valueType);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,valueType);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Delivering OTP by Push: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Validate User Token by Help Desk Call
    public ResponseObject executeOTPByHelpDesk(String auth,String query, AuthRequest authRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject responseObject =null;
        try{

            target = client.target(query);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(authRequest),MediaType.APPLICATION_JSON));

            responseObject=response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            responseObject = new ObjectMapper().readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Delivering OTP by HelpDesk: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return responseObject;

    }

    //Run IP Evaluation against user and IP Address
    public IPEval executeIPEval(String auth,String query, IPEvalRequest ipEvalRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        IPEval ipEval =null;

        try{
            target = client.target(query);

            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(ipEvalRequest), MediaType.APPLICATION_JSON));
            System.out.println(response.getStatus());
            //ipEval =response.readEntity(IPEval.class);

            responseStr= response.readEntity(String.class);
            System.out.println(responseStr);
            JAXBContext context = JAXBContext.newInstance(Response.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(MapperFeature.AUTO_DETECT_FIELDS, true);
            ipEval = objectMapper.readValue(inStream,IPEval.class);


        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Running IP Evaluation: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return ipEval;

    }

    //Run AccessHistories Post
    public ResponseObject executeAccessHistory(String auth, String query, AccessHistoryRequest accessHistoryRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        ResponseObject accessHistory =null;
        try{
            target = client.target(query);

            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(accessHistoryRequest),MediaType.APPLICATION_JSON));

            accessHistory = response.readEntity(ResponseObject.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationConfig.Feature.AUTO_DETECT_FIELDS, true);
            accessHistory = objectMapper.readValue(inStream,ResponseObject.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Running Access History POST: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return accessHistory;

    }
    // Run DFP Validate
    public DFPValidateResponse executeDFPValidate(String auth, String query, DFPValidateRequest dfpValidateRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        DFPValidateResponse dfpValidateResponse =null;
        try{
            target = client.target(query);

            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(dfpValidateRequest),MediaType.APPLICATION_JSON));

            dfpValidateResponse = response.readEntity(DFPValidateResponse.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationConfig.Feature.AUTO_DETECT_FIELDS, true);
            dfpValidateResponse = objectMapper.readValue(inStream,DFPValidateResponse.class);
             */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Running Access History POST: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return dfpValidateResponse;

    }

    // Run DFP Confirm
    public DFPConfirmResponse executeDFPConfirm(String auth, String query, DFPConfirmRequest dfpConfirmRequest, String ts)throws Exception{

        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String responseStr=null;
        DFPConfirmResponse dfpConfirmResponse =null;
        try{
            target = client.target(query);

            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    //type(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    post(Entity.entity(JSONUtil.getJSONStringFromObject(dfpConfirmRequest),MediaType.APPLICATION_JSON));

            dfpConfirmResponse =response.readEntity(DFPConfirmResponse.class);
            /*
            responseStr= response.getEntity(String.class);
            JAXBContext context = JAXBContext.newInstance(ResponseObject.class);
            context.createUnmarshaller();
            InputStream inStream = new ByteArrayInputStream(responseStr.getBytes());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationConfig.Feature.AUTO_DETECT_FIELDS, true);
            dfpConfirmResponse = objectMapper.readValue(inStream,DFPConfirmResponse.class);
            */

        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception Running Access History POST: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return dfpConfirmResponse;

    }

    //Get Factors for the user requested
    public <T> T executeGetJSObject(String auth, String query,String ts,  Class<T> valueType)throws Exception {
        if(client == null) {
            createConnection();
        }

        WebTarget target = null;
        Response response = null;
        String factors=null;
        T jsObjectResponse =null;
        try{

            target = client.target(query);
            //target.header("Authorization", auth);
            response = target.request().
                    accept(MediaType.APPLICATION_JSON).
                    header("Authorization", auth).
                    header("X-SA-Date", ts).
                    get();
            jsObjectResponse= response.readEntity(valueType);
            /*
            //System.out.println(factors);
            JAXBContext context = JAXBContext.newInstance(valueType);
            context.createUnmarshaller();

            InputStream inStream = new ByteArrayInputStream(factors.getBytes());
            jsObjectResponse = new ObjectMapper().readValue(inStream, valueType);
            */
        }catch(Exception e){
            logger.error(new StringBuilder().append("Exception getting JS Object SRC: \nQuery:\n\t")
                    .append(query).append("\nError:").append(e.getMessage()).append(".\nResponse code is ").append(response).toString(), e);
        }
        return jsObjectResponse;

    }
}
