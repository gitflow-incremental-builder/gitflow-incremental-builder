package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

public class Xpp3DomWrapperTest {

    @Test
    public void reflective_noChild() {
        var dom = new Xpp3Dom("configuration");

        var underTest = new DownstreamCalculator.Xpp3DomWrapper.Reflective(dom, null);

        assertThat(underTest.getChild("foo")).isNull();
    }

    @Test
    public void reflective_noValue() {
        var dom = new Xpp3Dom("configuration");
        dom.addChild(new Xpp3Dom("foo"));

        var underTest = new DownstreamCalculator.Xpp3DomWrapper.Reflective(dom, null);

        var child = underTest.getChild("foo");
        assertThat(child).isNotNull();
        assertThat(child.getValue()).isNull();
    }

    @Test
    public void reflective_hasValue() {
        var dom = new Xpp3Dom("configuration");
        var child = new Xpp3Dom("foo");
        child.setValue("expected");
        dom.addChild(child);

        var underTest = new DownstreamCalculator.Xpp3DomWrapper.Reflective(dom, null);

        var actualChild = underTest.getChild("foo");
        assertThat(actualChild).isNotNull();
        assertThat(actualChild.getValue()).isEqualTo(child.getValue());
    }
}
