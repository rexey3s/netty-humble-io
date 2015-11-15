package vn.eagleeye.research.network;

import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Chuong Dang on 11/15/2015.
 */
public class Client {
    public static class ByteBufMediaPacket extends DefaultByteBufHolder {
        int length;
        public ByteBufMediaPacket(ByteBuf data, int length) {
            super(data);
            this.length = length;
        }



    }

    static Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws AWTException, IOException, InterruptedException {
        final int snapsPerSecond = 24;
        final Robot robot = new Robot();
        final Toolkit toolkit = Toolkit.getDefaultToolkit();
        final Rectangle screenbounds = new Rectangle(toolkit.getScreenSize());
        final Rational framerate = Rational.make(1, snapsPerSecond);
        final Codec codec = Codec.findEncodingCodecByName("libx264");
        /**
         * Now that we know what codec, we need to create an encoder
         **/
        final Encoder encoder = Encoder.make(codec);
        /**
         * Video encoders need to know at a minimum:
         *   width
         *   height
         *   pixel format
         * Some also need to know frame-rate (older codecs that had a fixed rate at which video files could
         * be written needed this). There are many other options you can set on an encoder, but we're
         * going to keep it simpler here.
         */
        encoder.setWidth(screenbounds.width);
        encoder.setHeight(screenbounds.height);
        // We are going to use 420P as the format because that's what most video formats these days use
        final PixelFormat.Type pixelformat = PixelFormat.Type.PIX_FMT_YUV420P;
        encoder.setPixelFormat(pixelformat);
        encoder.setTimeBase(framerate);

        /** An annoynace of some formats is that they need global (rather than per-stream) headers,
         * and in that case you have to tell the encoder. And since Encoders are decoupled from
         * Muxers, there is no easy way to know this beyond
         */
//        if (format.getFlag(MuxerFormat.Flag.GLOBAL_HEADER))
//            encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);

        /** Open the encoder. */
        encoder.open(null, null);
        /** Next, we need to make sure we have the right MediaPicture format objects
         * to encode data with. Java (and most on-screen graphics programs) use some
         * variant of Red-Green-Blue image encoding (a.k.a. RGB or BGR). Most video
         * codecs use some variant of YCrCb formatting. So we're going to have to
         * convert. To do that, we'll introduce a MediaPictureConverter object later. object.
         */
        final MediaPictureConverter converter = null;
        final MediaPicture picture = MediaPicture.make(
                encoder.getWidth(),
                encoder.getHeight(),
                pixelformat);
        picture.setTimeBase(framerate);

        /** Now begin our main loop of taking screen snaps.
         * We're going to encode and then write out any resulting packets. */
        final io.humble.video.MediaPacket packet = io.humble.video.MediaPacket.make();
        Bootstrap b = new Bootstrap();
        EventLoopGroup worker = new NioEventLoopGroup();
        b.group(worker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                    }
                });
        final AtomicLong timestampAttr = new AtomicLong(0);
        ChannelFuture f = b.connect("127.0.0.1", 9090);
        final VideoHeader videoHeader = new VideoHeader("h264",snapsPerSecond,encoder.getWidth(),encoder.getHeight(),pixelformat.toString());
        f.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f1) throws Exception {
                byte[] header = SerializationUtils.serialize(videoHeader);

                f1.channel().writeAndFlush(Unpooled.wrappedBuffer(header)).addListener(new ChannelFutureListener() {
//
                    public void operationComplete(ChannelFuture f2) throws Exception {
//                        MediaPacket mp = MediaPacket.make();
//                        final BufferedImage screenShot = robot.createScreenCapture(screenbounds);
//
//                        final BufferedImage screen = convertToType(screenShot, BufferedImage.TYPE_3BYTE_BGR);
//                        final MediaPictureConverter converter1 = MediaPictureConverterFactory.createConverter(screen, picture);
//                        converter1.toPicture(picture, screen, timestampAttr.getAndIncrement());
//                        do {
//                            encoder.encode(mp, picture);
//                            if (packet.isComplete()) {
//                                byte[] data = packet.getData().getByteArray(0, packet.getData().getSize());
//                                f2.channel().writeAndFlush(new VideoPacket(packet.getSize(), data));
//                            }
//                        } while (packet.isComplete());
                        long l=0,time;
                        while(true){
                            time = System.currentTimeMillis();
                            /** Make the screen capture && convert image to TYPE_3BYTE_BGR */
                            final BufferedImage screenShot = robot.createScreenCapture(screenbounds);

                            final BufferedImage screen = convertToType(screenShot, BufferedImage.TYPE_3BYTE_BGR);
                            /** This is LIKELY not in YUV420P format, so we're going to convert it using some handy utilities. */
                            final MediaPictureConverter converter1 = MediaPictureConverterFactory.createConverter(screen, picture);

                            converter1.toPicture(picture, screen, l++);
                            do {
                                encoder.encode(packet, picture);
                                if (packet.isComplete()) {

//                                    logger.info("length: {}", packet.getSize());
//                                    out.writeBytes(ByteBuffer.allocateDirect(packet.getSize()));


                                    f2.channel().writeAndFlush(Unpooled.wrappedBuffer(packet.getData().getByteArray(0,packet.getData().getSize())));

                                }
                            } while (packet.isComplete());
                            /** now we'll sleep until it's time to take the next snapshot. */
                            /** now we'll sleep until it's time to take the next snapshot. */
                            long timeToSleep = (long) (1000 * framerate.getDouble()) - (System.currentTimeMillis() - time);
                            if (timeToSleep > 0)
                                Thread.sleep(timeToSleep);
                        }
                    }


                });

            }
        }).sync();



    }
    /**
     * Convert a {@link BufferedImage} of any type, to {@link BufferedImage} of a
     * specified type. If the source image is the same type as the target type,
     * then original image is returned, otherwise new image of the correct type is
     * created and the content of the source image is copied into the new image.
     *
     * @param sourceImage
     *          the image to be converted
     * @param targetType
     *          the desired BufferedImage type
     *
     * @return a BufferedImage of the specifed target type.
     *
     * @see BufferedImage
     */

    public static BufferedImage convertToType(BufferedImage sourceImage,
                                              int targetType)
    {
        BufferedImage image;

        // if the source image is already the target type, return the source image

        if (sourceImage.getType() == targetType)
            image = sourceImage;

            // otherwise create a new image of the target type and draw the new
            // image

        else
        {
            image = new BufferedImage(sourceImage.getWidth(),
                    sourceImage.getHeight(), targetType);
            image.getGraphics().drawImage(sourceImage, 0, 0, null);
        }

        return image;
    }
}
