/**
 * Mock for the Jenkins/Hudson object required for processing
 * the various steps.  This became very important after introducing
 * mocking as pretty much every class wants to know what's happening
 * within Jenkins.  Nothing here actually starts the process, and
 * provides a very bland "Jenkins" startup
 *
 * This mock technically should be for Jenkins.class.  However, 
 * ExtensionList pretty much requires that the object also be of 
 * type Hudson.  Since Mockito works by creating a subclass of the 
 * desired class, it has to be of Hudson.  This should be changed 
 * when ExtensionList no longer requires a Hudon object.
 */
 package hudson;

import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.model.Hudson; //only needed until Hudson reference is removed from the ExtensionList.
import jenkins.model.Jenkins;
import org.jenkinsci.pipeline_steps_doc_generator.HyperLocalPluginManger;

import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

 public class MockJenkins {
     private MockExtensionLists mockLookup = new MockExtensionLists();

     public Jenkins getMockJenkins(HyperLocalPluginManger pm) {
         Jenkins mockJenkins = mock(Hudson.class); //required by ExtensionList
         when(mockJenkins.getPluginManager()).thenReturn(pm);
         when(mockJenkins.getInitLevel()).thenReturn(InitMilestone.COMPLETED);

         doAnswer(new Answer<ExtensionList>() {
            @Override
            public ExtensionList answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return mockLookup.getMockExtensionList(pm, mockJenkins, (Class) args[0]);
            }
         }).when(mockJenkins).getExtensionList(any(Class.class));

         return mockJenkins;
     }
 }