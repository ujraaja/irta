package com.callgraph;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;

public class SaveActionListener implements IResourceChangeListener {

   IRTAView irtaView;

   public SaveActionListener(IRTAView irtaView) {
      this.irtaView = irtaView;
   }

   @Override
   public void resourceChanged(IResourceChangeEvent event) {
      irtaView.processCurrentFile();
   }
}
