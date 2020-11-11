/*******************************************************************************
 * Copyright (c) 2011 Google, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Google, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.wb.tests.designer.swing;

import org.eclipse.wb.internal.core.utils.reflect.ReflectionUtils;
import org.eclipse.wb.internal.swing.model.component.ComponentInfo;
import org.eclipse.wb.internal.swing.model.component.ContainerInfo;
import org.eclipse.wb.tests.gef.UIRunnable;
import org.eclipse.wb.tests.gef.UiContext;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;

/**
 * Support "Customize" tests.
 *
 * @author lobas_av
 * @author scheglov_ke
 */
public class CustomizeTest extends SwingModelTest {
  ////////////////////////////////////////////////////////////////////////////
  //
  // Exit zone :-) XXX
  //
  ////////////////////////////////////////////////////////////////////////////
  public void _test_exit() throws Exception {
    System.exit(0);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Tests
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_noCustomizer() throws Exception {
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    JButton button = new JButton('button');",
        "    add(button);",
        "  }",
        "}");
    panel.refresh();
    ComponentInfo button = panel.getChildrenComponents().get(0);
    // check action
    IMenuManager manager = getContextMenu(button);
    IAction action = findChildAction(manager, "&Customize...");
    assertNull(action);
  }

  public void test_customizer() throws Exception {
    setFileContentSrc(
        "test/MyButton.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public class MyButton extends JButton {",
            "}"));
    setFileContentSrc(
        "test/MyCustomizer.java",
        getTestSource(
            "import java.beans.Customizer;",
            "import java.beans.PropertyChangeListener;",
            "public class MyCustomizer extends JPanel implements Customizer {",
            "  public MyCustomizer() {",
            "    System.setProperty('wbp.test.isDesignTime',"
                + " Boolean.toString(java.beans.Beans.isDesignTime()));",
            "  }",
            "  public void setObject(Object bean) {",
            "  }",
            "  public void addPropertyChangeListener(PropertyChangeListener listener) {",
            "  }",
            "  public void removePropertyChangeListener(PropertyChangeListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // create bean info
    setFileContentSrc(
        "test/MyButtonBeanInfo.java",
        getTestSource(
            "import java.beans.BeanInfo;",
            "import java.beans.BeanDescriptor;",
            "import java.beans.Introspector;",
            "import java.beans.SimpleBeanInfo;",
            "public class MyButtonBeanInfo extends SimpleBeanInfo {",
            "  private BeanDescriptor m_descriptor;",
            "  public MyButtonBeanInfo() {",
            "    m_descriptor = new BeanDescriptor(MyButton.class, MyCustomizer.class);",
            "  }",
            "  public BeanDescriptor getBeanDescriptor() {",
            "    return m_descriptor;",
            "  }",
            "  public BeanInfo[] getAdditionalBeanInfo() {",
            "    try {",
            "      BeanInfo info = Introspector.getBeanInfo(JButton.class);",
            "      return new BeanInfo[] {info};",
            "    } catch (Throwable e) {",
            "    }",
            "    return null;",
            "  }",
            "}"));
    waitForAutoBuild();
    // create panel
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    MyButton button = new MyButton();",
        "    add(button);",
        "  }",
        "}");
    panel.refresh();
    ComponentInfo button = panel.getChildrenComponents().get(0);
    // check action
    IMenuManager manager = getContextMenu(button);
    final IAction action = findChildAction(manager, "&Customize...");
    assertNotNull(action);
    // open customize dialog
    new UiContext().executeAndCheck(new UIRunnable() {
      public void run(UiContext context) throws Exception {
        action.run();
      }
    }, new UIRunnable() {
      public void run(UiContext context) throws Exception {
        context.useShell("Customize");
        context.clickButton("OK");
      }
    });
    // check for isDesignTime()
    {
      String value = System.clearProperty("wbp.test.isDesignTime");
      assertEquals("true", value);
    }
    // check no changes
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    MyButton button = new MyButton();",
        "    add(button);",
        "  }",
        "}");
  }

  // XXX
  //TODO
  public void _test_customizer_chageProperties_OK() throws Exception {
    prepare_customizer_changeProperties();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    MyButton button = new MyButton();",
        "    add(button);",
        "  }",
        "}");
    panel.refresh();
    final ComponentInfo button = panel.getChildrenComponents().get(0);
    // check action
    IMenuManager manager = getContextMenu(button);
    final IAction action = findChildAction(manager, "&Customize...");
    assertNotNull(action);
    // open customize dialog
    new UiContext().executeAndCheck(new UIRunnable() {
      public void run(UiContext context) throws Exception {
        action.run();
      }
    }, new UIRunnable() {
      public void run(UiContext context) throws Exception {
        context.useShell("Customize");
        // change properties
        Object object = button.getObject();
        Object customizer = ReflectionUtils.getFieldObject(object, "customizer");
        ReflectionUtils.invokeMethod(customizer, "doBeanChanges()");
        // commit changes
        context.clickButton("OK");
      }
    });
    // check source
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    MyButton button = new MyButton();",
        "    button.setTitle('New title');",
        "    button.setFreeze(true);",
        "    add(button);",
        "  }",
        "}");
  }

  private void prepare_customizer_changeProperties() throws Exception {
    setFileContentSrc(
        "test/MyCustomizer.java",
        getTestSource(
            "import java.beans.Customizer;",
            "import java.beans.PropertyChangeListener;",
            "public class MyCustomizer extends JPanel implements Customizer {",
            "  private MyButton button;",
            "  public void setObject(Object bean) {",
            "    button = (MyButton) bean;",
            "    button.customizer = this;",
            "  }",
            "  public void doBeanChanges() {",
            "    button.setTitle('New title');",
            "    firePropertyChange('title', null, 'New title');",
            "    button.setFreeze(true);",
            "    firePropertyChange('freeze', null, true);",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyButton.java",
        getTestSource(
            "public class MyButton extends JButton {",
            "  public Object customizer;",
            "  private String m_title;",
            "  public String getTitle() {",
            "    return m_title;",
            "  }",
            "  public void setTitle(String title) {",
            "    m_title = title;",
            "  }",
            "  private boolean m_freeze;",
            "  public boolean isFreeze() {",
            "    return m_freeze;",
            "  }",
            "  public void setFreeze(boolean freeze) {",
            "    m_freeze = freeze;",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyButtonBeanInfo.java",
        getTestSource(
            "import java.beans.BeanInfo;",
            "import java.beans.BeanDescriptor;",
            "import java.beans.Introspector;",
            "import java.beans.SimpleBeanInfo;",
            "import java.beans.PropertyDescriptor;",
            "public class MyButtonBeanInfo extends SimpleBeanInfo {",
            "  private BeanDescriptor m_descriptor;",
            "  private PropertyDescriptor[] m_properties;",
            "  public MyButtonBeanInfo() {",
            "    m_descriptor = new BeanDescriptor(MyButton.class, MyCustomizer.class);",
            "    try {",
            "      m_properties = new PropertyDescriptor[2];",
            "      m_properties[0] = new PropertyDescriptor('title', MyButton.class, 'getTitle', 'setTitle');",
            "      m_properties[1] = new PropertyDescriptor('freeze', MyButton.class, 'isFreeze', 'setFreeze');",
            "    } catch(Throwable e) {",
            "    }",
            "  }",
            "  public BeanDescriptor getBeanDescriptor() {",
            "    return m_descriptor;",
            "  }",
            "  public PropertyDescriptor[] getPropertyDescriptors() {",
            "    return m_properties;",
            "  }",
            "  public BeanInfo[] getAdditionalBeanInfo() {",
            "    try {",
            "      BeanInfo info = Introspector.getBeanInfo(JButton.class);",
            "      return new BeanInfo[] {info};",
            "    } catch (Throwable e) {",
            "    }",
            "    return null;",
            "  }",
            "}"));
    waitForAutoBuild();
  }
}