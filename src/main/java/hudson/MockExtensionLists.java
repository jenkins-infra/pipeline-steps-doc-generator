/**
 * This mocks a few different
 */

package hudson;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.pipeline_steps_doc_generator.HyperLocalPluginManger;

import static org.mockito.Mockito.*;
import org.mockito.stubbing.Answer;
import org.mockito.invocation.InvocationOnMock;
import hudson.ExtensionList;
import hudson.model.Hudson;
import jenkins.model.Jenkins;

public class MockExtensionLists {
    private static Map<String, ExtensionList<?>> extensionLists = new HashMap<String, ExtensionList<?>>();

    public ExtensionList<?> getMockExtensionList(HyperLocalPluginManger hlpm, Jenkins hudson, Class<?> type) {
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

        public MockExtensionList(HyperLocalPluginManger hlpm, Jenkins hudson, Class<T> type) {
            ExtensionList<T> realList = ExtensionList.create(hudson, type);
            mockExtensionList = spy(realList);

            doReturn("Locking resources").when(mockExtensionList).getLoadLock();
            doAnswer(mockLoad(hlpm)).when(mockExtensionList).load();
        }

        private Answer<List<ExtensionComponent<T>>> mockLoad(HyperLocalPluginManger hlpm) {
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