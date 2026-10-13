package com.oldbutgold.shop.modules.communication.application;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class ChatMediaValidatorTest {
    byte[] png(){return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1cAAAAASUVORK5CYII=");}
    byte[] mp4(int seconds){var b=ByteBuffer.allocate(52);b.putInt(16).put("ftyp".getBytes()).put("mp42".getBytes()).putInt(0);b.putInt(36).put("moov".getBytes()).putInt(28).put("mvhd".getBytes()).putInt(0).putInt(0).putInt(0).putInt(1000).putInt(seconds*1000);return b.array();}
    @Test void recognizesImageAndHashesContent(){var v=ChatMediaValidator.validate(png(),"image/png");assertThat(v.type()).isEqualTo("IMAGE");assertThat(v.sha256()).hasSize(64);}
    @Test void rejectsMimeSpoofAndSvg(){assertThatThrownBy(()->ChatMediaValidator.validate(png(),"image/jpeg")).isInstanceOf(CommunicationException.class);assertThatThrownBy(()->ChatMediaValidator.validate("<svg/>".getBytes(),"image/svg+xml")).isInstanceOf(CommunicationException.class);}
    @Test void enforcesSizeAndEmpty(){assertThatThrownBy(()->ChatMediaValidator.validate(new byte[5242881],"image/png")).isInstanceOf(CommunicationException.class);assertThatThrownBy(()->ChatMediaValidator.validate(new byte[0],"video/mp4")).isInstanceOf(CommunicationException.class);}
    @Test void extractsMp4DurationAndRejectsLongVideo(){assertThat(ChatMediaValidator.validate(mp4(30),"video/mp4").duration()).isEqualTo(30);assertThatThrownBy(()->ChatMediaValidator.validate(mp4(31),"video/mp4")).isInstanceOf(CommunicationException.class);}
    @Test void rejectsMalformedMp4Boxes(){var data=mp4(10);ByteBuffer.wrap(data).putInt(16,999999);assertThatThrownBy(()->ChatMediaValidator.validate(data,"video/mp4")).isInstanceOf(CommunicationException.class);}
    @Test void rejectsHugeWebpDimensions(){var b=ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(22).put("WEBPVP8X".getBytes()).putInt(10).putInt(0).put(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255});assertThatThrownBy(()->ChatMediaValidator.validate(b.array(),"image/webp")).isInstanceOf(CommunicationException.class);}
}
