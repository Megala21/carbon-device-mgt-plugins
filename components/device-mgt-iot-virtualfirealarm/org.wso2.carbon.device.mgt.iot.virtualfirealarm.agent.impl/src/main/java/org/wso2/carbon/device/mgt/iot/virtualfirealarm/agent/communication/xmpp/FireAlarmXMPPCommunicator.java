/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.communication.xmpp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.packet.Message;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.core.AgentConstants;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.core.AgentManager;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.core.AgentUtilOperations;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.exception.AgentCoreOperationException;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.transport.TransportHandlerException;
import org.wso2.carbon.device.mgt.iot.virtualfirealarm.agent.transport.xmpp.XMPPTransportHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FireAlarmXMPPCommunicator extends XMPPTransportHandler {

    private static final Log log = LogFactory.getLog(FireAlarmXMPPCommunicator.class);

    private ScheduledExecutorService service = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> dataPushServiceHandler;
    private ScheduledFuture<?> connectorServiceHandler;

    private String username;
    private String password;
    private String resource;
    private String xmppAdminJID;
    private String xmppDeviceJID;

    public FireAlarmXMPPCommunicator(String server) {
        super(server);
    }

    public FireAlarmXMPPCommunicator(String server, int port) {
        super(server, port);
    }

    public FireAlarmXMPPCommunicator(String server, int port, int timeout) {
        super(server, port, timeout);
    }

    public ScheduledFuture<?> getDataPushServiceHandler() {
        return dataPushServiceHandler;
    }

    @Override
    public void connect() {
        final AgentManager agentManager = AgentManager.getInstance();
        username = agentManager.getAgentConfigs().getDeviceId();
        password = agentManager.getAgentConfigs().getAuthToken();
        resource = agentManager.getAgentConfigs().getDeviceOwner();

        xmppDeviceJID = username + "@" + server;
        xmppAdminJID = AgentConstants.XMPP_ADMIN_ACCOUNT_UNAME + "@" + server;


        Runnable connect = new Runnable() {
            public void run() {
                if (!isConnected()) {
                    try {
                        connectToServer();
                        loginToServer(username, password, resource);
                        agentManager.updateAgentStatus("Connected to XMPP Server");

                        setMessageFilterAndListener(xmppAdminJID, xmppDeviceJID, true);
                        publishDeviceData();

                    } catch (TransportHandlerException e) {
                        if (log.isDebugEnabled()) {
                            log.warn(AgentConstants.LOG_APPENDER +
                                             "Connection/Login to XMPP server at: " + server +
                                             " failed");
                        }
                    }
                }
            }
        };

        connectorServiceHandler = service.scheduleAtFixedRate(connect, 0, timeoutInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * This is an abstract method used for post processing the received XMPP-message. This
     * method will be implemented as per requirement at the time of creating an object of this
     * class.
     *
     * @param xmppMessage the xmpp message received by the listener.
     */
    @Override
    public void processIncomingMessage(Message xmppMessage, String... messageParams) {
        final AgentManager agentManager = AgentManager.getInstance();
        String from = xmppMessage.getFrom();
        String message = xmppMessage.getBody();
        String receivedMessage;
        String replyMessage;
        String securePayLoad;

        try {
            receivedMessage = AgentUtilOperations.extractMessageFromPayload(message);
            log.info(AgentConstants.LOG_APPENDER + "Message [" + receivedMessage + "] was received");
        } catch (AgentCoreOperationException e) {
            log.warn(AgentConstants.LOG_APPENDER + "Could not extract message from payload.", e);
            return;
        }

        String[] controlSignal = receivedMessage.split(":");
        //message- "<SIGNAL_TYPE>:<SIGNAL_MODE>" format. (ex: "BULB:ON", "TEMPERATURE", "HUMIDITY")

        try {
            switch (controlSignal[0].toUpperCase()) {
                case AgentConstants.BULB_CONTROL:
                    if (controlSignal.length != 2) {
                        replyMessage = "BULB controls need to be in the form - 'BULB:{ON|OFF}'";
                        log.warn(replyMessage);
                        securePayLoad = AgentUtilOperations.prepareSecurePayLoad(replyMessage);
                        sendXMPPMessage(xmppAdminJID, securePayLoad, "CONTROL-REPLY");
                        break;
                    }

                    agentManager.changeAlarmStatus(controlSignal[1].equals(AgentConstants.CONTROL_ON));
                    log.info(AgentConstants.LOG_APPENDER + "Bulb was switched to state: '" + controlSignal[1] + "'");
                    break;

                case AgentConstants.TEMPERATURE_CONTROL:
                    int currentTemperature = agentManager.getTemperature();

                    String replyTemperature =
                            "The current temperature was read to be: '" + currentTemperature +
                                    "C'";
                    log.info(AgentConstants.LOG_APPENDER + replyTemperature);

                    replyMessage = AgentConstants.TEMPERATURE_CONTROL + ":" + currentTemperature;
                    securePayLoad = AgentUtilOperations.prepareSecurePayLoad(replyMessage);
                    sendXMPPMessage(xmppAdminJID, securePayLoad, "CONTROL-REPLY");
                    break;

                case AgentConstants.HUMIDITY_CONTROL:
                    int currentHumidity = agentManager.getHumidity();

                    String replyHumidity = "The current humidity was read to be: '" + currentHumidity + "%'";
                    log.info(AgentConstants.LOG_APPENDER + replyHumidity);

                    replyMessage = AgentConstants.HUMIDITY_CONTROL + ":" + currentHumidity;
                    securePayLoad = AgentUtilOperations.prepareSecurePayLoad(replyMessage);
                    sendXMPPMessage(xmppAdminJID, securePayLoad, "CONTROL-REPLY");
                    break;

                default:
                    replyMessage = "'" + controlSignal[0] + "' is invalid and not-supported for this device-type";
                    log.warn(replyMessage);
                    securePayLoad = AgentUtilOperations.prepareSecurePayLoad(replyMessage);
                    sendXMPPMessage(xmppAdminJID, securePayLoad, "CONTROL-ERROR");
                    break;
            }
        } catch (AgentCoreOperationException e) {
            log.warn(AgentConstants.LOG_APPENDER + "Preparing Secure payload failed", e);
        }

    }


    @Override
    public void publishDeviceData() {
        final AgentManager agentManager = AgentManager.getInstance();
        int publishInterval = agentManager.getPushInterval();

        Runnable pushDataRunnable = new Runnable() {
            @Override
            public void run() {
                int currentTemperature = agentManager.getTemperature();
                String payLoad = AgentConstants.TEMPERATURE_CONTROL + ":" + currentTemperature;

                Message xmppMessage = new Message();
                xmppMessage.setTo(xmppAdminJID);
                xmppMessage.setSubject("PUBLISHER");
                xmppMessage.setBody(payLoad);
                xmppMessage.setType(Message.Type.chat);

                sendXMPPMessage(xmppAdminJID, xmppMessage);
                log.info(AgentConstants.LOG_APPENDER + "Message: '" + xmppMessage.getBody() +
                                 "' sent to XMPP JID [" + xmppAdminJID + "] under subject [" +
                                 xmppMessage.getSubject() + "]");
            }
        };

        dataPushServiceHandler = service.scheduleAtFixedRate(pushDataRunnable, publishInterval,
                                                             publishInterval, TimeUnit.SECONDS);
    }


    @Override
    public void disconnect() {
        Runnable stopConnection = new Runnable() {
            public void run() {
                while (isConnected()) {
                    dataPushServiceHandler.cancel(true);
                    connectorServiceHandler.cancel(true);
                    closeConnection();

                    if (log.isDebugEnabled()) {
                        log.warn(AgentConstants.LOG_APPENDER +
                                         "Unable to 'STOP' connection to XMPP server at: " +
                                         server);
                    }

                    try {
                        Thread.sleep(timeoutInterval);
                    } catch (InterruptedException e1) {
                        log.error(AgentConstants.LOG_APPENDER +
                                          "XMPP-Terminator: Thread Sleep Interrupt Exception");
                    }

                }
            }
        };

        Thread terminatorThread = new Thread(stopConnection);
        terminatorThread.setDaemon(true);
        terminatorThread.start();
    }


    @Override
    public void processIncomingMessage() {

    }

    @Override
    public void publishDeviceData(String... publishData) {

    }

}