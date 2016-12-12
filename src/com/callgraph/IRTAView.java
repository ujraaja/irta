package com.callgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

public class IRTAView extends ViewPart {
   
   private Text textField;
   IRTAMain irtaMain;
   private Set<String> processedFunctions;

   public IRTAView() {
      processedFunctions = new HashSet<>();
   }

   public void createPartControl(Composite parent) {
      textField = new Text(parent, SWT.H_SCROLL | SWT.V_SCROLL);
      textField.setText("Initial contents");
      irtaMain = new IRTAMain();
      ResourcesPlugin.getPlugin().getWorkspace().addResourceChangeListener(new SaveActionListener(this));
      processAllFiles();
   }
   
   private void rec(Node node, boolean flag) {
      if(node == null) return;
      String currFn = "";
      for(String s : irtaMain.functions.keySet())
         if(irtaMain.functions.get(s) == node)
            currFn = s;
      Assert.isTrue(!currFn.isEmpty());
      if(processedFunctions.contains(currFn)) return;
      processedFunctions.add(currFn);
      for(String s : node.functionCallsActive) {
         textField.append(currFn + " --> " + s + "\n");
         rec(irtaMain.functions.get(s), false);
      }
      if(flag && !node.functionCallsActive.isEmpty())
         textField.append("======================================\n\n");
   }
   
   public void processResult() {
      textField.setText("");
      processedFunctions.clear();
      for(Node node : irtaMain.roots) {
         rec(node, true);
      }
   }
   
   public void processCurrentFile() {
      IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
      if(workbenchWindow == null) return;
      IEditorPart editor = workbenchWindow.getActivePage().getActiveEditor();
      IEditorInput input= editor.getEditorInput();
      IFile file = ((IFileEditorInput) input).getFile();
      try {
         irtaMain.processFile(file);
      } catch (CoreException e) {
         e.printStackTrace();
         return;
      }
      processResult();
   }
   
   private void processAllFiles() {
      List<IResource> resources = new ArrayList<IResource>();
      
      try {
         resources.addAll(Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects()[0].members()));
         while(!resources.isEmpty()) {
            IResource resource = resources.get(0);
            resources.remove(0);
            if(resource.getType() == IResource.FILE && resource.getName().endsWith(".java")) {
               irtaMain.processFile((IFile) resource);
            } else if(resource.getType() == IResource.FOLDER) {
               resources.addAll(Arrays.asList(((IFolder) resource).members()));
            }
         }
      } catch (CoreException e) {
         e.printStackTrace();
         return;
      }
      
      processResult();
   }

   @Override
   public void setFocus() {
   }
}