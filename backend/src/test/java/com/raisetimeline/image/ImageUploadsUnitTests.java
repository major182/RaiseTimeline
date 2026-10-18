package com.raisetimeline.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.raisetimeline.common.logging.LogMarkers;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 画像を保存先に書けなかったときの単体テスト（ログ設計書 5 章）。 */
@DisplayName("UT-CO-02 画像の保存先に書けないとき")
class ImageUploadsUnitTests {

    @Test
    void 保存先に書けなければcriticalのログを出して例外をそのまま投げる() {
        ImageStorage storage = mock(ImageStorage.class);
        RuntimeException failure = new IllegalStateException("S3 に届かない");
        doThrow(failure).when(storage).put(anyString(), any(), anyString());
        ImageUploads uploads = new ImageUploads(storage);
        ImageUploads.CheckedImage image = uploads.check(TestImages.pngFile("file", 4, 3));

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(ImageUploads.class);
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> uploads.store(image, type -> ImageKeys.post(type, Instant.now()))).isSameAs(failure);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).containsExactly(LogMarkers.CRITICAL);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("S3 に届かない");
        });
    }
}
