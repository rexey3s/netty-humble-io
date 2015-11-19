package vn.eagleeye.research.network;

import io.humble.ferry.Buffer;
import io.humble.video.*;
import io.humble.video.awt.ImageFrame;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Chuong Dang on 11/15/2015.
 */
public class Server {
    static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static BufferedImage displayVideoAtCorrectTime(long streamStartTime,
                                                           final MediaPicture picture, final MediaPictureConverter converter,
                                                           BufferedImage image, final ImageFrame window, long systemStartTime,
                                                           final Rational systemTimeBase, final Rational streamTimebase)
            throws InterruptedException {
        long streamTimestamp = picture.getTimeStamp();
        // convert streamTimestamp into system units (i.e. nano-seconds)
        streamTimestamp = systemTimeBase.rescale(streamTimestamp-streamStartTime, streamTimebase);
        // get the current clock time, with our most accurate clock
        long systemTimestamp = System.nanoTime();
        // loop in a sleeping loop until we're within 1 ms of the time for that video frame.
        // a real video player needs to be much more sophisticated than this.
        while (streamTimestamp > (systemTimestamp - systemStartTime + 1000000)) {
            Thread.sleep(1);
            systemTimestamp = System.nanoTime();
        }
        // finally, convert the image from Humble format into Java images.
        image = converter.toImage(image, picture);
        // And ask the UI thread to repaint with the new image.
        window.setImage(image);
        return image;
    }
    final NioEventLoopGroup worker = new NioEventLoopGroup();
    final ServerBootstrap b = new ServerBootstrap();
    Rational streamTimebase = null;
    Rational systemTimeBase = null;

    final int videoStreamId = -1;
    long streamStartTime = Global.NO_PTS;
    long systemStartTime = 0;
    BufferedImage image = null;

    ImageFrame window;
    MediaPicture mediaPicture;
    MediaPictureConverter mediaPictureConverter ;
    Decoder videoDecoder;
    int snapsPerSecond;
    Codec codec;
    int width,height;
    public Server() {
        window = ImageFrame.make();
    }
    public void run() {

        b.group(worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {

                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new ByteToMessageDecoder() {
                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

                                if(in.readableBytes() < 4) {
                                    return;
                                }
                                byte[] headerData = new byte[in.readableBytes()];
                                in.readBytes(headerData);
                                VideoHeader videoHeader = SerializationUtils.deserialize(headerData);
                                System.out.println("Enter 1 ");

                                codec = Codec.findDecodingCodecByName(videoHeader.getCodec());
                                snapsPerSecond = videoHeader.getSnapsPerSecond();
                                videoDecoder = Decoder.make(codec);
                                width = videoHeader.getWidth();
                                height = videoHeader.getHeight();

                                streamTimebase = Rational.make(1,snapsPerSecond);
                                /**
                                 * Now we have found the audio stream in this file.  Let's open up our decoder so it can
                                 * do work.
                                 */
                                videoDecoder.open(null, null);

                                videoDecoder.setWidth(width);
                                videoDecoder.setHeight(height);
                                videoDecoder.setPixelFormat(PixelFormat.Type.valueOf(videoHeader.getPixelFormat()));
                                mediaPicture = MediaPicture.make(
                                        width,
                                        height,
                                        PixelFormat.Type.valueOf(videoHeader.getPixelFormat()));
                                /** A converter object we'll use to convert the picture in the video to a BGR_24 format that Java Swing
                                 * can work with. You can still access the data directly in the MediaPicture if you prefer, but this
                                 * abstracts away from this demo most of that byte-conversion work. Go read the source code for the
                                 * converters if you're a glutton for punishment.
                                 */
                                mediaPictureConverter =
                                        MediaPictureConverterFactory.createConverter(
                                                MediaPictureConverterFactory.HUMBLE_BGR_24, mediaPicture);
                                /**
                                 * This is the Window we will display in. See the code for this if you're curious, but to keep this demo clean
                                 * we're 'simplifying' Java AWT UI updating code. This method just creates a single window on the UI thread, and blocks
                                 * until it is displayed.
                                 */
                                final ImageFrame window = ImageFrame.make();
                                if (window == null) {
                                    throw new RuntimeException("Attempting this demo on a headless machine, and that will not work. Sad day for you.");
                                }
                                // Calculate the time BEFORE we start playing.
                                systemStartTime = System.nanoTime();
                                // Set units for the system time, which because we used System.nanoTime will be in nanoseconds.
                                systemTimeBase = Rational.make(1, 1000000000);
                                ctx.pipeline().remove(this);
//                                final VideoPacketDecoder videoFrameDecoder = new VideoPacketDecoder();

                                ctx.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4 ,0, 4));
                                ctx.pipeline().addLast("bytesDecoder", new ByteArrayDecoder());
                                ctx.pipeline().addLast(new SimpleChannelInboundHandler<byte[]>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
                                        assert msg.length != 0;
                                        logger.info("LENGTH {} RAW {}", msg.length, Arrays.toString(msg));

                                        videoDecoder.decode(mediaPicture,MediaPacket.make(Buffer.make(null, msg,0, msg.length)),0);
                                        displayVideoAtCorrectTime(streamStartTime, mediaPicture,
                                                mediaPictureConverter, image, window, systemStartTime,
                                                systemTimeBase,
                                                streamTimebase);
                                    }
                                });



                            }
                        });

                    }
                });
        try {
            b.bind(9090).sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    public static class VideoPacketDecoder extends ReplayingDecoder<VideoPacket.DecoderState> {
        public int getLength() {
            return length;
        }

        public int length;
        public VideoPacketDecoder() {
            super(VideoPacket.DecoderState.READ_LENGTH);
        }
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            switch (state()) {
                case READ_LENGTH:
                    length = in.readShort();
                    logger.info("LENGTH: {}", length);
                    checkpoint(VideoPacket.DecoderState.READ_CONTENT);
                case READ_CONTENT:
                    ByteBuf frame = in.readBytes(in.readableBytes());
                    checkpoint(VideoPacket.DecoderState.READ_LENGTH);
                    out.add(frame);
                    break;
                default:
                    throw new Error("Shouldn't reach here.");
            }
        }
    }
    public static void main(String[] args) throws AWTException, IOException {

        final Server server = new Server();
        server.run();
    }
}
