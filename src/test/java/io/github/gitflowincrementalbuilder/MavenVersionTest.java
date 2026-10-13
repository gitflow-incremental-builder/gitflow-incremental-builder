package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.apache.maven.rtinfo.internal.DefaultRuntimeInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import io.github.gitflowincrementalbuilder.util.LoggerSpyUtil;

@ExtendWith(MockitoExtension.class)
class MavenVersionTest {

    private Logger loggerSpy = LoggerSpyUtil.buildSpiedLoggerFor(MavenVersion.class);

    @Spy
    private DefaultRuntimeInformation runtimeInformation;

    @InjectMocks
    private MavenVersion underTest;

    @ParameterizedTest(name = "isMaven4OrHigher({0}) = {1}")
    @CsvSource({
            "'',false",
            "3.10.0,false",
            "4.0.0-alpha-1,true",
            "4.0.0-rc-7,true",
            "4.0.0,true",
            "4.1.0,true"
    })
    void isMaven4OrHigher_versions(String version, boolean expected) {
        doReturn(version).when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isMaven4OrHigher()).isEqualTo(expected);
    }

    @Test
    void getVersionString() {
        doReturn("3.9.9").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.getVersionString()).isEqualTo("3.9.9");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getVersionString_invalid(String version) {
        doReturn(version).when(runtimeInformation).getMavenVersion();

        assertThat(underTest.getVersionString()).isNull();
        verify(loggerSpy).warn(contains("Could not determine Maven version"));
    }

    @ParameterizedTest(name = "isSupported({0}) = {1}")
    @CsvSource({
            "'',false",
            "4.0.0,true",
            "4.0.0-rc7,true",
            "4.0.0-rc6,false",
            "3.10.0,true",
            "3.11.0,false",
            "3.12.0,false",
            "3.9.16,true",
            "3.8.9,true",
            "3.6.3,true",
            "4.1.0,false",
            "4.1.0-alpha-1,false",
            "4.1.0-rc-1,false",
            "3.6.2,false"
    })
    void isSupported_versions(String version, boolean expectedSupported) {
        doReturn(version).when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isEqualTo(expectedSupported);
    }
}
