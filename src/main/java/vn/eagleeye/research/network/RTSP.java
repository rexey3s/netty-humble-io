package vn.eagleeye.research.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chuong Dang on 11/19/2015.
 */
public class RTSP {
    public static class RtspClient implements Runnable {
        Logger logger = LoggerFactory.getLogger(getClass());

        final Bootstrap tcpBootstrap;
        //        final Bootstrap udpBootstrap;
        final NioEventLoopGroup workerGroup;


        public RtspClient() {
            workerGroup = new NioEventLoopGroup();
            tcpBootstrap = new Bootstrap();
            tcpBootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
//                            pipeline.addLast("udpEnvelop", new ChannelOutboundHandlerAdapter() {
//                                @Override
//                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
//                                    if(msg instanceof ByteBuf) {
//                                        logger.debug("Wrap with datagrampacket");
//                                        ctx.write(new DatagramPacket((ByteBuf) msg, new InetSocketAddress("192.168.5.86", 554)));
//                                    }
//                                }
//                            });
                            pipeline.addLast("encoder", new RtspEncoder());
                            pipeline.addLast("decoder", new RtspDecoder());
                            pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
                            pipeline.addLast("handler", new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    logger.debug("client connected");
                                    HttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, "rtsp://192.168.5.86/Great.Migrations.EP01.Born.To.Move.1080i.HDTV.AVC_BHT.mkv");
                                    req.headers().add(RtspHeaders.Names.CSEQ, 2);
//                                    req.headers().add(RtspHeaders.Names.REQUIRE, "implicit-play");
//                                    req.headers().add(RtspHeaders.Names.PROXY_REQUIRE, "gzipped-messages");
//                                    HttpRequest req =new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, "rtsp://192.168.5.86/Great.Migrations.EP01.Born.To.Move.1080i.HDTV.AVC_BHT.mkv");

                                    ctx.writeAndFlush(req);


                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (msg instanceof DefaultFullHttpRequest) {
                                        DefaultFullHttpRequest request = (DefaultFullHttpRequest) msg;
                                        logger.debug("client request ========>\n{}\n\n{}", request.toString(), request.content()
                                                .toString(CharsetUtil.UTF_8));
//                                        rtspServerStackImpl.processRtspRequest(request, ctx.channel());
                                    } else if (msg instanceof FullHttpResponse) {
                                        FullHttpResponse resp = (FullHttpResponse) msg;

                                        logger.debug("sever response ========>\n{}\n\n{}", resp.toString(), resp.content()
                                                .toString(CharsetUtil.UTF_8));
                                    }
                                }

                                @Override
                                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                    super.channelReadComplete(ctx);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    cause.printStackTrace();
                                    super.exceptionCaught(ctx, cause);
                                }
                            });
                        }
                    });

        }

        public void run() {
            try {

                ChannelFuture cf = tcpBootstrap.connect("192.168.5.86", 554).sync();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public static void main(String[] args) {
        final RtspClient rtspClient = new RtspClient();
        rtspClient.run();
    }
}
