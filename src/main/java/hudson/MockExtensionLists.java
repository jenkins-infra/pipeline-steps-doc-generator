/**
 * This mocks a few different
 */

package hudson;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jenkinsci.pipeline_steps_doc_generator.HyperLocalPluginManager;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import hudson.model.Hudson;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;

public class MockExtensionLists {
    private static Map<String, ExtensionList<?>> extensionLists = new HashMap<String, ExtensionList<?>>();

    public ExtensionList<?> getMockExtensionList(HyperLocalPluginManager hlpm, Jenkins hudson, Class<?> type) {
        if (SaveableListener.class.equals(type)) {
            ExtensionList<?> ret = mock(ExtensionList.class);
            doReturn(Collections.emptyIterator()).when(ret).iterator();
            return ret;
        }
        if(extensionLists.get(type.getName()) != null) {
            return extensionLists.get(type.getName());
        } else {
            MockExtensionList<?> mockList = new MockExtensionList<>(hlpm, hudson, type);
            extensionLists.put(type.getName(), mockList.getMockExtensionList());
            return mockList.getMockExtensionList();
        }
    }

    private class MockExtensionList<T> {
        ExtensionList<T> mockExtensionList;

        public MockExtensionList(HyperLocalPluginManager hlpm, Jenkins hudson, Class<T> type) {
            ExtensionList<T> realList = ExtensionList.create(hudson, type);
            mockExtensionList = spy(realList);

            doReturn("Locking resources").when(mockExtensionList).getLoadLock();
            doAnswer(mockLoad(hlpm)).when(mockExtensionList).load();
        }

        private Answer<List<ExtensionComponent<T>>> mockLoad(HyperLocalPluginManager hlpm) {
            return new Answer<List<ExtensionComponent<T>>>() {
                public List<ExtensionComponent<T>> answer(InvocationOnMock invocation) throws Throwable {
                    return hlpm.getPluginStrategy().findComponents(mockExtensionList.extensionType, (Hudson)null);
                }
            };
        }

        public ExtensionList<T> getMockExtensionList(){
            return mockExtensionList;
        }
    }
}