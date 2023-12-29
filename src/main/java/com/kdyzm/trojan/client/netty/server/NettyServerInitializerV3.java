package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.trojan.client.netty.inbound.TrojanClient2DestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.TrojanClient2DestInboundHandlerV3;
import com.kdyzm.trojan.client.netty.inbound.TrojanDest2ClientInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandlerV3;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import com.kdyzm.trojan.client.netty.util.IpUtil;
import com.kdyzm.trojan.client.netty.util.SslUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class NettyServerInitializerV3 extends ChannelInitializer<SocketChannel> {

    private final ConfigProperties configProperties;

    private final ConfigUtil configUtil;

    private final EventLoopGroup clientWorkGroup;

    public NettyServerInitializerV3(EventLoopGroup clientWorkGroup, ConfigProperties configProperties, ConfigUtil configUtil) {
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

        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("first", new ChannelHandler() {// 用于占位的handler
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

            }
        });
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpProxyInboundHandlerV3(configUtil.getPacModelMap(), configProperties, clientWorkGroup));


        ChannelHandlerContext ctx = ch.pipeline().firstContext();// 获取用于占位的handler
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture future = bootstrap.group(clientWorkGroup)
                .channel(NioSocketChannel.class)
                .remoteAddress(configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                        ch.pipeline().addLast(new TrojanRequestEncoder());
                        ch.pipeline().addLast(new TrojanDest2ClientInboundHandler(ctx));
                    }
                })
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .connect();
        future.get();
        pipeline.addLast(new TrojanClient2DestInboundHandlerV3(future));
    }
}
