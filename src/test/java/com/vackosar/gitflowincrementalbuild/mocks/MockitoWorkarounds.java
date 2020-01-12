package com.vackosar.gitflowincrementalbuild.mocks;

import java.lang.reflect.Method;

import org.mockito.internal.stubbing.defaultanswers.ForwardsInvocations;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Provides workarounds for bugs in Mockito.
 */
public class MockitoWorkarounds {

    public static class AdditionalAnswers {

        // workaround for https://github.com/mockito/mockito/issues/407 based on https://github.com/mockito/mockito/pull/412/files 
        @SuppressWarnings("unchecked")
        public static <T> Answer<T> delegatesTo(Object delegate) {
            return (Answer<T>) new PatchedForwardsInvocations(delegate);
        }

        private static class PatchedForwardsInvocations extends ForwardsInvocations {

            private static final long serialVersionUID = 1L;

            public PatchedForwardsInvocations(Object delegatedObject) {
                super(delegatedObject);
            }

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return super.answer(new InvocationOnMockWrapper(invocation) {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object[] getArguments() {
                        return ((Invocation) invocation).getRawArguments();
                    }
                });
            }
        }
    }

    private static class InvocationOnMockWrapper implements InvocationOnMock {

        private static final long serialVersionUID = 1L;

        private final InvocationOnMock delegate;

        public InvocationOnMockWrapper(InvocationOnMock delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object getMock() {
            return delegate.getMock();
        }

        @Override
        public Method getMethod() {
            return delegate.getMethod();
        }

        @Override
        public Object[] getArguments() {
            return delegate.getArguments();
        }

        @Override
        public <T> T getArgumentAt(int index, Class<T> clazz) {
            return delegate.getArgumentAt(index, clazz);
        }

        @Override
        public Object callRealMethod() throws Throwable {
            return delegate.callRealMethod();
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
