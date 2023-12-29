package com.kdyzm.trojan.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TestMain {

    /**
     * trojan远程服务的密码
     */
    private static final String _password = "用自己的";
    /**
     * trojan远程服务地址
     */
    private static final String _host = "用自己的";



    public static final int IPV4 = 0X01;

    public static final int DOMAIN = 0X03;

    public static final int IPV6 = 0x04;

    /**
     * trojan远程服务端口
     */
    private static int _port = 443;

    /**
     * 用于构建第一个请求包。注意：DST.ADDR这个字段需要填写域名，不能带有协议。
     */
    private static final String _uri = "www.google.com";
    /**
     * 构建http协议内容。注意：必须带有协议
     */
    private static final String _uri_http = "http://www.google.com/";
    private static final int _uri_port = 80;

    public static void main(String[] args) throws Throwable {
        /**
         * 构建客户端。与远程建立TLS连接
         */
        EventLoopGroup clientWorkGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture future = bootstrap.group(clientWorkGroup)
                .channel(NioSocketChannel.class)
                .remoteAddress(_host, _port)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build()
                                .newHandler(ch.alloc()));// 处理TSL连接
                        ch.pipeline().addLast(new HttpResponseDecoder());// 节码
                        ch.pipeline().addLast("http-aggregator",
                                new HttpObjectAggregator(65536));// 粘包拆包。注意：若不使用此handler，下一个handler的入参将不会是FullHttpResponse的子类，将无法打印content
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof FullHttpResponse) {
                                    FullHttpResponse httpResponse = (FullHttpResponse) msg;
                                    System.out.println("status:" + httpResponse.status().code());
                                    System.out.println(httpResponse.headers());
                                    System.out.println(httpResponse.content().toString(StandardCharsets.UTF_8));
                                    System.out.println("===============\r\n\r\n\r\n\r\n======================");
                                }
                                super.channelRead(ctx, msg);
                            }
                        });
                    }
                })
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .connect();
        future.get();// 同步等待客户端建立完成

        Channel channel = future.channel();


        /**
         * 发送第一个包，建立Trojan连接
         */
        final ByteBuf payload = generatePayload(_uri, _uri_http);

        ByteBuf out = Unpooled.buffer(1024);// 开始构建第一个trojan请求。第一个trojan请求中需要携带很多信息，之后的请求中不需要。
        String password = encryptThisString(_password);// hex(SHA224(password))
        out.writeCharSequence(password, StandardCharsets.UTF_8);
        out.writeByte(0X0D);// CRLF
        out.writeByte(0X0A);
        out.writeByte(1);// CMD
        out.writeByte(DOMAIN);// ATYP
        encodeAddress(DOMAIN, out, _uri);// DST.ADDR
        out.writeShort(_uri_port);// DST.PORT
        out.writeByte(0X0D);// CRLF
        out.writeByte(0X0A);
        out.writeBytes(payload.copy());// 这里必须用copy，否则使用一次之后payload就不能用了。因为rdx已经右移到wdx，没有可读内容。

        channel.writeAndFlush(out);

        System.out.println("init over:" + channel.isActive());

        /**
         * 直接将http包发送到远程
         */
        while (true) {
            Thread.sleep(1000);
            channel.writeAndFlush(payload.copy());
        }
    }

    private static ByteBuf generatePayload(String uri, String uriHttp) {
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.set("User-Agent", "curl/8.4.0");// 这些header都是按照"curl -x 127.0.0.1:10810 www.google.com"这个指令编写的，具体含义暂时不讨论
        headers.set("Accept", "*/*");
        headers.set("Proxy-Connection", "Keep-Alive");
        headers.set("Host", uri);
        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uriHttp, headers);// 创建访问www.google.com的http请求

        EmbeddedChannel em = new EmbeddedChannel(new HttpRequestEncoder());
        em.writeOutbound(req);// 将http请求转换成ByteBuf
        final ByteBuf payload = (ByteBuf) em.readOutbound();
        em.close();
        return payload;
    }

    private static void encodeAddress(int addressType, ByteBuf out, String dstAddr) {
        if (addressType == IPV4) {
            String[] split = dstAddr.split("\\.");
            for (String item : split) {
                int b = Integer.parseInt(item);
                out.writeByte(b);
            }
        } else if (addressType == DOMAIN) {
            out.writeByte(dstAddr.length());
            out.writeCharSequence(dstAddr, StandardCharsets.UTF_8);
        } else {
            //TODO 暂时不支持ipV6
            throw new RuntimeException("无法支持的地址类型");
        }
    }

    public static String encryptThisString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-224");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder hashtext = new StringBuilder(no.toString(16));
            while (hashtext.length() < 32) {
                hashtext.insert(0, "0");
            }
            return hashtext.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
