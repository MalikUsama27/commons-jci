/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package org.apache.commons.jci2.core;

 import java.io.File;

 import org.apache.commons.jci2.core.classes.ExtendedDump;
 import org.apache.commons.jci2.core.classes.SimpleDump;
 import org.apache.commons.jci2.core.listeners.ReloadingListener;
 import org.apache.commons.jci2.fam.monitor.FilesystemAlterationMonitor;
 import org.apache.commons.logging.Log;
 import org.apache.commons.logging.LogFactory;
 import org.junit.Before;
 import org.junit.Test;
 
 
 /**
  * Tests the ReloadingClassLoader for class reloading and event handling.
  */
 public final class ReloadingClassLoaderTestCase extends AbstractTestCase {
 
     private final Log log = LogFactory.getLog(ReloadingClassLoaderTestCase.class);
 
     private ReloadingClassLoader classloader;
     private ReloadingListener listener;
     private FilesystemAlterationMonitor fam;
 
     private final byte[] clazzSimple1;
     private final byte[] clazzSimple2;
     private final byte[] clazzExtended;
 
     public ReloadingClassLoaderTestCase() throws Exception {
         clazzSimple1 = SimpleDump.dump("Simple1");
         clazzSimple2 = SimpleDump.dump("Simple2");
         clazzExtended = ExtendedDump.dump();
         assertTrue("Simple1 class content is empty", clazzSimple1.length > 0);
         assertTrue("Simple2 class content is empty", clazzSimple2.length > 0);
         assertTrue("Extended class content is empty", clazzExtended.length > 0);
     }
 
     @Before
     public void setUp() throws Exception {
         super.setUp();
 
         classloader = new ReloadingClassLoader(this.getClass().getClassLoader());
         listener = new ReloadingListener();
 
         listener.addReloadNotificationListener(classloader);
 
         fam = new FilesystemAlterationMonitor();
         fam.addListener(directory, listener);
         fam.start();
     }
 
     @Test
     public void testCreate() throws Exception {
         listener.waitForFirstCheck();
 
         log.debug("Creating class");
         writeFile("jci2/Simple.class", clazzSimple1);
         listener.waitForCheck();
 
         final Object simple = classloader.loadClass("jci2.Simple").getConstructor().newInstance();
         assertEquals("Simple1", simple.toString());
     }
 
     @Test
     public void testChange() throws Exception {
         listener.waitForFirstCheck();
 
         log.debug("Creating class");
         writeFile("jci2/Simple.class", clazzSimple1);
         listener.waitForCheck();
 
         final Object simple1 = classloader.loadClass("jci2.Simple").getConstructor().newInstance();
         assertEquals("Simple1", simple1.toString());
 
         log.debug("Changing class");
         writeFile("jci2/Simple.class", clazzSimple2);
         listener.waitForEvent();
 
         final Object simple2 = classloader.loadClass("jci2.Simple").getConstructor().newInstance();
         assertEquals("Simple2", simple2.toString());
     }
 
     @Test
     public void testDelete() throws Exception {
         listener.waitForFirstCheck();
 
         log.debug("Creating class");
         writeFile("jci2/Simple.class", clazzSimple1);
         listener.waitForCheck();
 
         final Object simple = classloader.loadClass("jci2.Simple").getConstructor().newInstance();
         assertEquals("Simple1", simple.toString());
 
         log.debug("Deleting class");
         assertTrue("Failed to delete class file", new File(directory, "jci2/Simple.class").delete());
         listener.waitForEvent();
 
         try {
             classloader.loadClass("jci2.Simple").getConstructor().newInstance();
             fail("Class should not be found after deletion");
         } catch (final ClassNotFoundException e) {
             assertEquals("jci2.Simple", e.getMessage());
         }
     }
 
     @Test
     public void testDeleteDependency() throws Exception {
         listener.waitForFirstCheck();
 
         log.debug("Creating classes");
         writeFile("jci2/Simple.class", clazzSimple1);
         writeFile("jci2/Extended.class", clazzExtended);
         listener.waitForCheck();
 
         final Object simple = classloader.loadClass("jci2.Simple").getConstructor().newInstance();
         assertEquals("Simple1", simple.toString());
 
         final Object extended = classloader.loadClass("jci2.Extended").getConstructor().newInstance();
         assertEquals("Extended:Simple1", extended.toString());
 
         log.debug("Deleting class dependency");
         assertTrue("Failed to delete class file", new File(directory, "jci2/Simple.class").delete());
         listener.waitForEvent();
 
         try {
             classloader.loadClass("jci2.Extended").getConstructor().newInstance();
             fail("Extended class should fail to load after Simple class is deleted");
         } catch (final NoClassDefFoundError e) {
             assertEquals("jci2/Simple", e.getMessage());
         }
     }
 
     @Test
     public void testClassNotFound() {
         try {
             classloader.loadClass("bla");
             fail("Class 'bla' should not be found");
         } catch (final ClassNotFoundException e) {
             // Expected behavior
         }
     }
 
     @Test
     public void testDelegation() {
         classloader.clearAssertionStatus();
         classloader.setClassAssertionStatus("org.apache.commons.jci2.core.core.ReloadingClassLoader", true);
         classloader.setDefaultAssertionStatus(false);
         classloader.setPackageAssertionStatus("org.apache.commons.jci2.core.core", true);
         // FIXME: compare with delegation (assert that class delegation works)
     }
 
     @Override
     protected void tearDown() throws Exception {
         fam.removeListener(listener);
         fam.stop();
         super.tearDown();
     }
 }
 