package com.callgraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {

   // objectName -> createdType
   Map<String, Set<String>> liveTypes;
   
   // objectName -> definedType
   Map<String, String> rawTypes;

   Set<String> functionCalls;
   
   // actually referenced functions
   Set<String> functionCallsActive;

   Set<String> returnsTo;

   public Node() {
      liveTypes = new HashMap<String, Set<String>>();
      rawTypes = new HashMap<String, String>();
      functionCalls = new HashSet<String>();
      functionCallsActive = new HashSet<String>();
      returnsTo = new HashSet<String>();
   }
}
