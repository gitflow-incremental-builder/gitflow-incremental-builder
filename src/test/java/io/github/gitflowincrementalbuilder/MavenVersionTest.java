package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import org.apache.maven.rtinfo.internal.DefaultRuntimeInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MavenVersionTest {

    @Spy
    private DefaultRuntimeInformation runtimeInformation;

    @InjectMocks
    private MavenVersion underTest;

    @Test
    void isMaven4OrHigher_positive() {
        doReturn("4.0.0-rc-7").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isMaven4OrHigher()).isTrue();
    }

    @Test
    void isMaven4OrHigher_negative() {
        doReturn("3.10.0").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isMaven4OrHigher()).isFalse();
    }

    @Test
    void getVersionString() {
        doReturn("3.9.9").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.getVersionString()).isEqualTo("3.9.9");
    }

    @Test
    void isSupported_null() {
        doReturn(null).when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isFalse();
    }

    @Test
    void isSupported_maven40() {
        doReturn("4.0.0").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven40rc() {
        doReturn("4.0.0-rc7").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven310() {
        doReturn("3.10.0").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven39() {
        doReturn("3.9.16").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven38() {
        doReturn("3.8.9").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven363() {
        doReturn("3.6.3").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven41() {
        doReturn("4.1.0").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isFalse();
    }
    
    @Test
    void isSupported_maven41alpha() {
        doReturn("4.1.0-alpha-1").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isFalse();
    }

    @Test
    void isSupported_maven41rc() {
        doReturn("4.1.0-rc-1").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isFalse();
    }

    @Test
    void isSupported_unsupported() {
        doReturn("3.6.2").when(runtimeInformation).getMavenVersion();

        assertThat(underTest.isSupported()).isFalse();
    }
}
