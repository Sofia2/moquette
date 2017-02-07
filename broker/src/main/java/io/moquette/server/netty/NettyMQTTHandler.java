/*
 * Copyright (c) 2012-2017 The original author or authorsgetRockQuestions()
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.moquette.server.netty;

import io.moquette.parser.proto.Utils;
import io.moquette.parser.proto.messages.*;
import io.moquette.spi.impl.ProtocolProcessor;
import static io.moquette.parser.proto.messages.AbstractMessage.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.ConcurrentSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author andrea
 */
@Sharable
public class NettyMQTTHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(NettyMQTTHandler.class);
    private final ProtocolProcessor m_processor;
    private final ConcurrentSet<ChannelHandlerContext> channelHandlerContexts;

    public NettyMQTTHandler(ProtocolProcessor processor) {
        m_processor = processor;
        channelHandlerContexts = new ConcurrentSet<ChannelHandlerContext>();
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	super.channelActive(ctx);
    	channelHandlerContexts.add(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        AbstractMessage msg = (AbstractMessage) message;
        String messageType = Utils.msgType2String(msg.getMessageType());
        if (LOG.isDebugEnabled()) {
        	LOG.debug("Processing MQTT message. MessageType = {}.", messageType);
        }
        try {
            switch (msg.getMessageType()) {
                case CONNECT:
                    m_processor.processConnect(ctx.channel(), (ConnectMessage) msg);
                    break;
                case SUBSCRIBE:
                    m_processor.processSubscribe(ctx.channel(), (SubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE:
                    m_processor.processUnsubscribe(ctx.channel(), (UnsubscribeMessage) msg);
                    break;
                case PUBLISH:
                    m_processor.processPublish(ctx.channel(), (PublishMessage) msg);
                    break;
                case PUBREC:
                    m_processor.processPubRec(ctx.channel(), (PubRecMessage) msg);
                    break;
                case PUBCOMP:
                    m_processor.processPubComp(ctx.channel(), (PubCompMessage) msg);
                    break;
                case PUBREL:
                    m_processor.processPubRel(ctx.channel(), (PubRelMessage) msg);
                    break;
                case DISCONNECT:
                    m_processor.processDisconnect(ctx.channel());
                    break;
                case PUBACK:
                    m_processor.processPubAck(ctx.channel(), (PubAckMessage) msg);
                    break;
                case PINGREQ:
                    PingRespMessage pingResp = new PingRespMessage();
                    ctx.writeAndFlush(pingResp);
                    break;
            }
        } catch (Throwable ex) {
			LOG.error(
					"An unexpected exception was caught while processing MQTT message. MessageType = {}, cause = {}, errorMessage = {}.",
					messageType, ex.getCause(), ex.getMessage());
			ctx.fireExceptionCaught(ex);
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
			LOG.info("Notifying connection lost event. MqttClientId = {}.", clientID);
            m_processor.processConnectionLost(clientID, ctx.channel());
        }
        channelHandlerContexts.remove(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOG.error(
				"An unexpected exception was caught while processing MQTT message. Closing Netty channel. MqttClientId = {}, cause = {}, errorMessage = {}.",
				NettyUtils.clientID(ctx.channel()), cause.getCause(), cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            m_processor.notifyChannelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }
    
    public int getConnectionsNo() {
    	return this.channelHandlerContexts.size();
    }

}
