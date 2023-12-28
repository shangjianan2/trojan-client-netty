package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final ConfigProperties configProperties;

    private final ConfigUtil configUtil;

    private final EventLoopGroup clientWorkGroup;

    public NettyServerInitializer(EventLoopGroup clientWorkGroup, ConfigProperties configProperties, ConfigUtil configUtil) {
        this.configProperties = configProperties;
        this.configUtil = configUtil;
        this.clientWorkGroup = clientWorkGroup;
    }

    /**
     * 根据不同的端口号创建不同的pipeline
     *
     * @param ch
     * @throws Exception
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        int localPort = ch.localAddress().getPort();
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpProxyInboundHandler(configUtil.getPacModelMap(), configProperties, clientWorkGroup));
    }
}
