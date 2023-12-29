package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.models.TrojanRequest;
import com.kdyzm.trojan.client.netty.models.TrojanWrapperRequest;
import com.kdyzm.trojan.client.netty.util.SocksServerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/4/29
 */
@Slf4j
@RequiredArgsConstructor
public class TrojanClient2DestInboundHandlerV3 extends ChannelInboundHandlerAdapter {

    private final ChannelFuture dstChannelFuture;

    enum State {
        /**
         *
         */
        INIT,
        SUCCESS
    }

    private State state = State.INIT;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.trace("转发客户端的请求到代理服务器");
        if (!(msg instanceof TrojanWrapperRequest)) {
            return;
        }
        TrojanWrapperRequest trojanWrapperRequest = (TrojanWrapperRequest) msg;
        if (dstChannelFuture.channel().isActive()) {
            if (state == State.INIT) {
                dstChannelFuture.channel().writeAndFlush(trojanWrapperRequest);
                state = State.SUCCESS;
            } else {
                dstChannelFuture.channel().writeAndFlush(trojanWrapperRequest.getPayload());
            }
        } else {
            log.info("释放内存");
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("客户端与代理服务器的连接已经断开，即将断开代理服务器和目标服务器的连接");
        if (dstChannelFuture.channel().isActive()) {
            SocksServerUtils.closeOnFlush(dstChannelFuture.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("TrojanClient2DestInboundHandler exception", cause);
        ctx.close();
    }


}
