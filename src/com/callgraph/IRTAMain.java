package com.callgraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProvider;

public class IRTAMain {

   // class -> it's hash value
   Map<String, Integer> hashValues;

   // child -> parent
   Map<String, String> classParent;

   List<Node> roots;

   // key: class.function(parameter,s..)
   Map<String, Node> functions;

   Map<String, List<String>> classFunctions;

   private List<String> commonDatatypes;
   private List<String> cClassFunctions;
   private String className;

   private String sClass = "\\s*(?:public)\\s+(?:static\\s+)?(?:class)\\s+(\\w+)\\s+(?:extends\\s+(\\w+))?.*";
   private Pattern pClass = Pattern.compile(sClass);

   private String sFunction = "\\s*(?:public|private|protected)?\\s+(?:static\\\\s+)?(\\w+)\\s+(\\w+)\\((.*)\\).*";
   private Pattern pFunction = Pattern.compile(sFunction);
   
   private String sObjDecl = "\\s*(\\w+)\\s+(\\w+).*";
   private Pattern pObjDecl = Pattern.compile(sObjDecl);
   
   private String sObjDefn = "\\s*(?:\\w+\\s+)?(\\w+)\\s*=\\s*(?:new )\\s*(\\w+)\\(.*\\).*";
   private Pattern pObjDefn = Pattern.compile(sObjDefn);
   
   private String sFnCall = "\\s*(?:[\\w]+[^\\w]+)*(\\w+)\\.(\\w+)\\((.*)\\).*";
   private Pattern pFnCall = Pattern.compile(sFnCall);
   
   private String sLocalFnCall = "\\s*(?:[\\w]+[^\\w]+)*(\\w+)\\((.*)\\).*";
   private Pattern pLocalFnCall = Pattern.compile(sLocalFnCall);

   public IRTAMain() {
      hashValues = new HashMap<String, Integer>();
      classParent = new HashMap<String, String>();
      roots = new ArrayList<Node>();
      functions = new HashMap<String, Node>();
      classFunctions = new HashMap<String, List<String>>();
      commonDatatypes = new ArrayList<String>();
      cClassFunctions = new ArrayList<String>();
      
      commonDatatypes.add("String");
      commonDatatypes.add("int");
   }
   
   public void processFile(IFile file) throws CoreException {
      
      String fileName = file.getName();
      
      System.out.println(fileName);

      IDocumentProvider provider = new TextFileDocumentProvider();
      provider.connect(file);
      IDocument document = provider.getDocument(file);

      if(document == null) return;
      
      int hashValue = document.get().hashCode();
      
      if(hashValues.containsKey(fileName) && hashValues.get(fileName).equals(hashValue)) return;

      hashValues.put(fileName, hashValue);

      int numberOfLines = document.getNumberOfLines();
      
      className = null;
      for(int i = 0; i < numberOfLines; i++) {
         String line = null;
         try {
            int length = document.getLineLength(i);
            int offset = document.getLineOffset(i);
            line = document.get(offset, length).trim();
            if(className == null) {
               if(line.matches(sClass))
                  className = processClass(line);
            } else {
               if(line.matches(sFunction))
                  i = processFunction(document, i)-1;
            }
         } catch (BadLocationException e) {
            e.printStackTrace();
            return;
         }
      }

      for(String s : classFunctions.get(className)) {
         Node node = functions.get(s);
         List<String> list = new ArrayList<String>();
         if(!cClassFunctions.contains(s)) {
            for(String ss : functions.keySet())
               if(node == functions.get(ss))
                  list.add(ss);
            for(String ss : node.returnsTo)
               functions.get(ss).functionCallsActive.remove(node);
         }
         for(String ss : list)
            functions.remove(ss);
      }
      
      classFunctions.get(className).clear();
      classFunctions.get(className).addAll(cClassFunctions);
      
      createGraph();

      cClassFunctions.clear();
      populateRoots();
   }
   
   private void populateRoots() {
      roots.clear();
      for(Node node : functions.values())
//         if(node.returnsTo.isEmpty())
            roots.add(node);
   }
   
   private void createGraph() {
      for(String function : functions.keySet()) {
         Node node = functions.get(function);
         node.functionCallsActive.clear();
         for(String rawCall : node.functionCalls) {
            String obj = rawCall.substring(0, rawCall.indexOf('.'));
            String fnCall = rawCall.substring(rawCall.indexOf('.') + 1);
            if(node.liveTypes.get(obj) == null) {
               Node n = functions.get(rawCall);
               node.functionCallsActive.add(rawCall);
               n.returnsTo.add(function);
            } else
               for(String liveType : node.liveTypes.get(obj)) {
                  while(true) {
                     String s = liveType + "." + fnCall;
                     Node n = functions.get(s);
                     if(n != null) {
                        node.functionCallsActive.add(s);
                        n.returnsTo.add(function);
                        break;
                     }
                     if(!classParent.containsKey(liveType) || liveType.equals(classParent.get(liveType)))
                        break;
                     liveType = classParent.get(liveType);
                  }
               }
         }
      }
   }
   
   private String processParameter(String parameter) {
      parameter = parameter.substring(0, parameter.lastIndexOf(' '));
      return parameter;
   }

   private String processClass(String line) {
      Matcher mClass = pClass.matcher(line);
      Assert.isTrue(mClass.find());
      String cClass = mClass.group(1);
      String parent = mClass.group(2);
      if(parent != null && !parent.isEmpty()) {
         if(classParent.containsKey(cClass)) {
            if(!classParent.get(cClass).equals(parent)) {
               classParent.put(cClass, parent);
            }
         } else
            classParent.put(cClass, parent);
      } else
         classParent.put(cClass, cClass);

      if(classFunctions.get(cClass) == null)
         classFunctions.put(cClass, new ArrayList<String>());
      for(String s : classFunctions.get(cClass)) {
         Node node = functions.get(s);
         for(String ss : node.functionCallsActive)
            functions.get(ss).returnsTo.remove(s);
         node.functionCallsActive.clear();
      }
      return cClass;
   }
   
   private int processFunction(IDocument document, int lineNumber) throws BadLocationException {
      int length = document.getLineLength(lineNumber);
      int offset = document.getLineOffset(lineNumber);
      lineNumber++;
      String line = document.get(offset, length).trim();
      Matcher mFunction = pFunction.matcher(line);
      Assert.isTrue(mFunction.find());
      String functionName = mFunction.group(2);
      String[] params = mFunction.group(3).split(",");
      String key = className + "." + functionName + "(";
      if(!params[0].isEmpty()) {
         for(int i = 0; i < params.length; i++)
            key += processParameter(params[i].trim()).trim() + ",";
         key = key.substring(0, key.length()-1);
      }
      key += ")";
      cClassFunctions.add(key);
      Node node = functions.get(key);
      if(node == null) {
         node = new Node();
         functions.put(key, node);
      }
      Assert.isTrue(node.functionCallsActive.size() == 0);
      node.liveTypes.clear();
      node.rawTypes.clear();
      node.functionCalls.clear();
      for(int i = 0; i < params.length; i++) {
         String[] param = params[i].trim().split(" ");
         if(param.length == 2) {
            node.liveTypes.put(param[1], new HashSet<String>());
            node.liveTypes.get(param[1]).add(param[0]);
         }
      }
      int openBraces = 0;
      if(line.contains("{")) openBraces++;
      while(openBraces == 0) {
         length = document.getLineLength(lineNumber);
         offset = document.getLineOffset(lineNumber);
         line = document.get(offset, length).trim();
         if(line.contains("{")) {
            openBraces++;
            break;
         }
         lineNumber++;
      }
      while(openBraces > 0) {
         length = document.getLineLength(lineNumber);
         offset = document.getLineOffset(lineNumber);
         line = document.get(offset, length).trim();
         if(line.contains("{"))
            openBraces++;
         if(line.contains("}"))
            openBraces--;
         if(line.matches(sObjDecl)) {
            Matcher matcher = pObjDecl.matcher(line);
            Assert.isTrue(matcher.find());
            if(commonDatatypes.contains(matcher.group(1))) {
               node.liveTypes.put(matcher.group(2), new HashSet<String>());
               node.liveTypes.get(matcher.group(2)).add(matcher.group(1));
            } else
               node.rawTypes.put(matcher.group(2), matcher.group(1));
         }
         if(line.matches(sObjDefn)) {
            Matcher matcher = pObjDefn.matcher(line);
            Assert.isTrue(matcher.find());
            String obj = matcher.group(1);
            String type = matcher.group(2);
            if(node.liveTypes.get(obj) == null)
               node.liveTypes.put(obj, new HashSet<String>());
            node.liveTypes.get(obj).add(type);
         }
         else if(line.matches(sFnCall)) {
            Matcher matcher = pFnCall.matcher(line);
            Assert.isTrue(matcher.find());
            String obj = matcher.group(1);
            String fn = matcher.group(2);
            params = matcher.group(3).split(",");
            key = obj + "." + fn + "(";
            if(params[0].isEmpty())
               params = new String[0];
            node.functionCalls.addAll(rec(0, node, params, key));
         }
         else if(line.matches(sLocalFnCall)) {
            Matcher matcher = pLocalFnCall.matcher(line);
            Assert.isTrue(matcher.find());
            String fn = matcher.group(1);
            if(!fn.equals(functionName) && !fn.equals("if") && !fn.equals("for") && !fn.equals("while")) {
               params = matcher.group(2).split(",");
               key = className + "." + fn + "(";
               if(params[0].isEmpty())
                  params = new String[0];
               node.functionCalls.addAll(rec(0, node, params, key));
            }
         }
         lineNumber++;
      }
      return lineNumber;
   }
   
   private List<String> rec(int ind, Node node, String[] params, String s) {
      List<String> list = new ArrayList<String>();
      if(ind == params.length) {
         list.add(s.replaceAll(",$", "") + ")");
         return list;
      }
      Set<String> liveTypes = node.liveTypes.get(params[ind].trim());
      for(String liveType : liveTypes) {
         list.addAll(rec(ind+1, node, params, s + liveType + ","));
      }
      return list;
   }
}
