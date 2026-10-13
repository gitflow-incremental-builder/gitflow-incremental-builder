package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.maven.rtinfo.RuntimeInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MavenVersionTest {

    @Mock
    private RuntimeInformation runtimeInformationMock;

    @InjectMocks
    private MavenVersion underTest;

    @Test
    void isMaven4OrHigher() {
        when(runtimeInformationMock.isMavenVersion(MavenVersion.MAVEN_4_OR_LATER)).thenReturn(true);

        assertThat(underTest.isMaven4OrHigher()).isTrue();
        verify(runtimeInformationMock).isMavenVersion(MavenVersion.MAVEN_4_OR_LATER);
    }

    @Test
    void getVersionString() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.9.9");

        assertThat(underTest.getVersionString()).isEqualTo("3.9.9");
    }

    @Test
    void isSupported_null() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn(null);

        assertThat(underTest.isSupported()).isFalse();
    }

    @Test
    void isSupported_maven40rc() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("4.0.0-rc7");

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven310() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.10.0");

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven39() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.9.16");

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven38() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.8.9");

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_maven363() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.6.3");

        assertThat(underTest.isSupported()).isTrue();
    }

    @Test
    void isSupported_unsupported() {
        when(runtimeInformationMock.getMavenVersion()).thenReturn("3.6.2");

        assertThat(underTest.isSupported()).isFalse();
    }
}
