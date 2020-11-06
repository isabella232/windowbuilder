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
package org.eclipse.wb.tests.designer.core.model.property;

import org.eclipse.wb.core.model.JavaInfo;
import org.eclipse.wb.internal.core.editor.DesignPageSite;
import org.eclipse.wb.internal.core.model.property.Property;
import org.eclipse.wb.internal.core.model.property.category.PropertyCategory;
import org.eclipse.wb.internal.core.model.property.editor.PropertyEditor;
import org.eclipse.wb.internal.core.model.property.event.EventsProperty;
import org.eclipse.wb.internal.core.model.property.event.EventsPropertyUtils;
import org.eclipse.wb.internal.core.model.property.event.IPreferenceConstants;
import org.eclipse.wb.internal.core.model.property.table.PropertyTable;
import org.eclipse.wb.internal.core.model.util.ObjectsLabelProvider;
import org.eclipse.wb.internal.core.utils.execution.ExecutionUtils;
import org.eclipse.wb.internal.core.utils.execution.RunnableEx;
import org.eclipse.wb.internal.core.utils.reflect.ReflectionUtils;
import org.eclipse.wb.internal.core.utils.ui.MenuManagerEx;
import org.eclipse.wb.internal.swing.model.component.ComponentInfo;
import org.eclipse.wb.internal.swing.model.component.ContainerInfo;
import org.eclipse.wb.tests.designer.swing.SwingModelTest;
import org.eclipse.wb.tests.gef.UIRunnable;
import org.eclipse.wb.tests.gef.UiContext;

import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EventObject;

/**
 * Test for {@link EventsProperty}.
 *
 * @author scheglov_ke
 */
public class EventsPropertyTest extends SwingModelTest implements IPreferenceConstants {
  ////////////////////////////////////////////////////////////////////////////
  //
  // Life cycle
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setPreferencesDefaults();
  }

  @Override
  protected void tearDown() throws Exception {
    waitEventLoop(0);
    setPreferencesDefaults();
    super.tearDown();
  }

  /**
   * Resets the event preferences to defaults.
   */
  public static void setPreferencesDefaults() {
    setPreferencesDefaults(
        org.eclipse.wb.internal.swing.Activator.getDefault().getPreferenceStore());
  }

  /**
   * Resets the event preferences to defaults in given {@link IPreferenceStore}.
   */
  private static void setPreferencesDefaults(IPreferenceStore preferences) {
    // type
    preferences.setToDefault(P_CODE_TYPE);
    preferences.setToDefault(P_INNER_POSITION);
    // stub
    preferences.setToDefault(P_CREATE_STUB);
    preferences.setToDefault(P_STUB_NAME_TEMPLATE);
    preferences.setToDefault(P_DELETE_STUB);
    // inner
    preferences.setToDefault(P_INNER_NAME_TEMPLATE);
    // other
    preferences.setToDefault(P_FINAL_PARAMETERS);
    preferences.setToDefault(P_DECORATE_ICON);
  }

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
  // Parsing
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_noListeners() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertFalse(eventsProperty.isModified());
    assertSame(Property.UNKNOWN_VALUE, eventsProperty.getValue());
    eventsProperty.setValue(null); // ignored
  }

  public void test_hasListener() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "        System.out.println('keyPressed!');",
        "      }",
        "      public void keyReleased(KeyEvent e) {",
        "        System.out.println('keyReleased!');",
        "      }",
        "    });",
        "  }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertNotNull(eventsProperty);
    assertTrue(eventsProperty.isModified());
    assertEquals("[key]", getPropertyText(eventsProperty));
    // check listeners
    Property keyProperty;
    {
      Property[] listenerProperties = getSubProperties(eventsProperty);
      assertEquals(13, listenerProperties.length);
      // no listener for "focus"
      {
        Property focusProperty = getPropertyByTitle(listenerProperties, "focus");
        assertNotNull(focusProperty);
        assertFalse(focusProperty.isModified());
        assertEquals("[]", getPropertyText(focusProperty));
      }
      // check "key" listener property
      keyProperty = getPropertyByTitle(listenerProperties, "key");
      assertNotNull(keyProperty);
      assertTrue(keyProperty.isModified());
      assertEquals("[pressed, released]", getPropertyText(keyProperty));
    }
    // check listener methods
    {
      Property[] methodProperties = getSubProperties(keyProperty);
      assertEquals(3, methodProperties.length);
      assertEquals("pressed", methodProperties[0].getTitle());
      assertEquals("released", methodProperties[1].getTitle());
      assertEquals("typed", methodProperties[2].getTitle());
    }
  }

  public void test_listenerInVariable() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    KeyListener listener = new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "    };",
        "    addKeyListener(listener);",
        "  }",
        "}");
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    assertNotNull(keyPressedProperty);
    assertTrue(keyPressedProperty.isModified());
  }

  public void test_listenerInnerType() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  private class KeyHandler extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "      System.out.println('keyPressed!');",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "      System.out.println('keyReleased!');",
        "    }",
        "  }",
        "  Test() {",
        "    addKeyListener(new KeyHandler());",
        "  }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertEquals("[key]", getPropertyText(eventsProperty));
    //
    Property[] listenerProperties = getSubProperties(eventsProperty);
    Property keyProperty = getPropertyByTitle(listenerProperties, "key");
    assertEquals("[pressed, released]", getPropertyText(keyProperty));
  }

  /**
   * When component has "addXListener()" methods with same name, but different parameters, we should
   * show qualified listener names to user.
   */
  public void test_qualifeidListenerNames() throws Exception {
    setFileContentSrc(
        "test/Listener_1.java",
        getSourceDQ("package test;", "public interface Listener_1 {", "  void foo();", "}"));
    setFileContentSrc(
        "test/Listener_2.java",
        getSourceDQ("package test;", "public interface Listener_2 {", "  void bar();", "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(Listener_1 listener) {",
            "  }",
            "  public void addMyListener(Listener_2 listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    //
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // Listener_1.foo()
    {
      Property property = getEventsListenerMethod(panel, "my(test.Listener_1)", "foo");
      assertNotNull(property);
    }
    // Listener_2.bar()
    {
      Property property = getEventsListenerMethod(panel, "my(test.Listener_2)", "bar");
      assertNotNull(property);
    }
    // simple name
    {
      Property property = getEventsListenerMethod(panel, "key", "pressed");
      assertNotNull(property);
    }
  }

  /**
   * "this" listener handler.<br>
   * No conditions that check {@link EventObject#getSource()}, so all listener methods are handlers.
   */
  public void test_listenerThis_1() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      System.out.println('keyPressed!');",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "      System.out.println('keyReleased!');",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "      System.out.println('keyTyped!');",
        "    }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertEquals("[key]", getPropertyText(eventsProperty));
    //
    Property[] listenerProperties = getSubProperties(eventsProperty);
    Property keyProperty = getPropertyByTitle(listenerProperties, "key");
    assertEquals("[pressed, released, typed]", getPropertyText(keyProperty));
  }

  /**
   * "this" listener handler.<br>
   * Conditions that check {@link EventObject#getSource()}.
   */
  public void test_listenerThis_2() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  private final JButton m_button = new JButton();",
        "  Test() {",
        "    add(m_button);",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      // valid routing IfStatement",
        "      if (e.getSource() == this) {",
        "        onThis_keyPressed(e);",
        "      }",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "      if (e.getSource() == m_button) {",
        "        // do nothing, we need this only to show EventsProperty",
        "        // that here we have routing to stubs",
        "      }",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "      // no any statement, so no stubs routing, so is handler",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertEquals("[key]", getPropertyText(eventsProperty));
    //
    Property[] listenerProperties = getSubProperties(eventsProperty);
    Property keyProperty = getPropertyByTitle(listenerProperties, "key");
    assertEquals("[pressed, typed]", getPropertyText(keyProperty));
  }

  /**
   * "this" listener handler.<br>
   * {@link Statement} as {@link IfStatement#THEN_STATEMENT_PROPERTY}.
   */
  public void test_listenerThis_3() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  private final JButton m_button = new JButton();",
        "  Test() {",
        "    add(m_button);",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      // valid routing IfStatement, single Statement (not Block) as 'then'",
        "      if (e.getSource() == this)",
        "        onThis_keyPressed(e);",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "      if (e.getSource() == m_button) {",
        "        // do nothing, we need this only to show EventsProperty",
        "        // that here we have routing to stubs",
        "      }",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "      if (e.getSource() == m_button) {",
        "        // do nothing, we need this only to show EventsProperty",
        "        // that here we have routing to stubs",
        "      }",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    assertEquals("[key]", getPropertyText(eventsProperty));
    //
    Property[] listenerProperties = getSubProperties(eventsProperty);
    Property keyProperty = getPropertyByTitle(listenerProperties, "key");
    assertEquals("[pressed]", getPropertyText(keyProperty));
  }

  /**
   * <code>m_button</code> is used in <code>keyPressed</code>, and we check if it represents
   * {@link JavaInfo}, however <code>keyPressed</code> is not part of execution flow, so we don't
   * know that some {@link JavaInfo} was assigned into <code>m_button</code> (at least 20080516), so
   * don't understand routing to stub.
   */
  public void test_listenerAndExecutionFlow() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  private JButton m_button;",
        "  Test() {",
        "    m_button = new JButton();",
        "    add(m_button);",
        "    m_button.addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      if (e.getSource() == m_button) {",
        "        onButton_keyPressed(e);",
        "      }",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "      // no any statement, so no stubs routing, so is handler",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "      // no any statement, so no stubs routing, so is handler",
        "    }",
        "    private void onButton_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    ComponentInfo button = panel.getChildrenComponents().get(0);
    EventsProperty eventsProperty = (EventsProperty) button.getPropertyByTitle("Events");
    assertEquals("[key]", getPropertyText(eventsProperty));
    //
    Property[] listenerProperties = getSubProperties(eventsProperty);
    Property keyProperty = getPropertyByTitle(listenerProperties, "key");
    assertEquals("[pressed, released, typed]", getPropertyText(keyProperty));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Delete
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Deletes value of given {@link Property} and clicks "OK" in confirmation dialog.
   */
  private static void deleteEventPropertyWithGUI(final Property property) throws Exception {
    new UiContext().executeAndCheck(new UIRunnable() {
      public void run(UiContext context) throws Exception {
        property.setValue(Property.UNKNOWN_VALUE);
      }
    }, new UIRunnable() {
      public void run(UiContext context) throws Exception {
        context.useShell("Confirm");
        context.clickButton("OK");
      }
    });
  }

  public void test_delete_method_noListener() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
    // do delete, no GUI expected
    Property keyReleasedProperty = getEventsListenerMethod(panel, "key", "released");
    keyReleasedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  public void test_delete_method_noMethod() throws Exception {
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    // do delete, no GUI expected
    Property keyReleasedProperty = getEventsListenerMethod(panel, "key", "released");
    keyReleasedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
  }

  public void test_delete_method_Cancel() throws Exception {
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    final Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    String expectedSource = m_lastEditor.getSource();
    // press "Cancel", so don't delete
    new UiContext().executeAndCheck(new UIRunnable() {
      public void run(UiContext context) throws Exception {
        keyPressedProperty.setValue(Property.UNKNOWN_VALUE);
      }
    }, new UIRunnable() {
      public void run(UiContext context) throws Exception {
        context.useShell("Confirm");
        context.clickButton("Cancel");
      }
    });
    // no change expected
    assertEditor(expectedSource, m_lastEditor);
  }

  public void test_delete_method() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    //
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    deleteEventPropertyWithGUI(keyPressedProperty);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  public void test_delete_methodWithStub() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "        do_keyPressed(e);",
        "      }",
        "    });",
        "  }",
        "  public void do_keyPressed(KeyEvent e) {",
        "  }",
        "}");
    //
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    deleteEventPropertyWithGUI(keyPressedProperty);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  public void test_delete_listener() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyPressed(KeyEvent e) {",
        "      }",
        "      public void keyReleased(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    //
    Property keyProperty = getEventsListener(panel, "key");
    deleteEventPropertyWithGUI(keyProperty);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
    assertEquals(m_lastEditor.getSource(), m_lastEditor.getModelUnit().getSource());
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Delete and inner class
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_deleteInner_method() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new MyKeyListener());",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    // prepare property
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    assertTrue(keyPressedProperty.isModified());
    // do delete
    deleteEventPropertyWithGUI(keyPressedProperty);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  public void test_deleteInner_listener() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    addKeyListener(new MyKeyListener());",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    // prepare property
    Property keyProperty = getEventsListener(panel, "key");
    assertTrue(keyProperty.isModified());
    // do delete
    deleteEventPropertyWithGUI(keyProperty);
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  /**
   * When we delete component, we should also delete its inner {@link TypeDeclaration} used as
   * listener.
   */
  public void test_deleteInner_componentWithListener() throws Exception {
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    JButton button = new JButton();",
        "    add(button);",
        "    button.addKeyListener(new MyKeyListener());",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    panel.refresh();
    ComponentInfo button = panel.getChildrenComponents().get(0);
    // do delete "button"
    button.delete();
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "  }",
        "}");
  }

  /**
   * When we delete component, we should delete only its event handlers.
   */
  public void test_deleteComponent_andOtherListeners() throws Exception {
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button_1 = new JButton();",
        "      add(button_1);",
        "      button_1.addKeyListener(new KeyAdapter() {});",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new KeyAdapter() {});",
        "    }",
        "  }",
        "}");
    panel.refresh();
    ComponentInfo button_1 = panel.getChildrenComponents().get(0);
    // do delete "button"
    button_1.delete();
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new KeyAdapter() {});",
        "    }",
        "  }",
        "}");
  }

  /**
   * When delete {@link JavaInfo}, in general we should also delete its "inner listener", so we may
   * ask about multiple usages. But we should not do this.
   */
  public void test_deleteInner_listener_twoUsages_deleteJavaInfo() throws Exception {
    parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "      button.addKeyListener(new MyKeyListener());",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new MyKeyListener());",
        "    }",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    JavaInfo button = getJavaInfoByName("button");
    // do delete
    button.delete();
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new MyKeyListener());",
        "    }",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
  }

  public void test_deleteInner_listener_twoUsages_keepOne() throws Exception {
    parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "      button.addKeyListener(new MyKeyListener());",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new MyKeyListener());",
        "    }",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    JavaInfo button = getJavaInfoByName("button");
    // prepare property
    Property keyProperty = getEventsListener(button, "key");
    assertTrue(keyProperty.isModified());
    // do delete
    deleteInnerListener_twoUsages(keyProperty, "&No");
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new MyKeyListener());",
        "    }",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
  }

  public void test_deleteInner_listener_twoUsages_removeAll() throws Exception {
    parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "      button.addKeyListener(new MyKeyListener());",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "      button_2.addKeyListener(new MyKeyListener());",
        "    }",
        "  }",
        "  private class MyKeyListener extends KeyAdapter {",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "  }",
        "}");
    JavaInfo button = getJavaInfoByName("button");
    // prepare property
    Property keyProperty = getEventsListener(button, "key");
    assertTrue(keyProperty.isModified());
    // do delete
    deleteInnerListener_twoUsages(keyProperty, "&Yes");
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "    }",
        "  }",
        "}");
  }

  private static void deleteInnerListener_twoUsages(final Property property,
      final String multiButton) throws Exception {
    new UiContext().executeAndCheck(new UIRunnable() {
      public void run(UiContext context) throws Exception {
        property.setValue(Property.UNKNOWN_VALUE);
      }
    }, new UIRunnable() {
      public void run(final UiContext context) throws Exception {
        context.useShell("Confirm");
        context.clickButton("OK");
        ExecutionUtils.runAsync(new RunnableEx() {
          public void run() throws Exception {
            context.useShell("Confirm");
            context.clickButton(multiButton);
          }
        });
      }
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Delete and "this" handler
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * "this" listener handler.<br>
   * When delete component: its stubs, routing to stubs should be removed.
   */
  public void test_deleteThis_listener() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  private final JButton m_button = new JButton();",
        "  Test() {",
        "    add(m_button);",
        "    m_button.addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      // valid routing IfStatement",
        "      if (e.getSource() == m_button) {",
        "        onButton_keyPressed(e);",
        "      }",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onButton_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    panel.refresh();
    // delete "button"
    ComponentInfo button = panel.getChildrenComponents().get(0);
    button.delete();
    assertEditor(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "}");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Delete stub methods
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * "this" listener handler, delete single stub.
   */
  public void test_delete_method_interfaceWithDirectStub() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      onThis_keyPressed(e);",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    // do delete, no GUI expected
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    keyPressedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "}");
  }

  /**
   * "this" listener handler, delete single stub.
   */
  public void test_delete_method_interfaceWithConditionalStub_block() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      if (e.getSource() == this) {",
        "        onThis_keyPressed(e);",
        "      }",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    // do delete, no GUI expected
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    keyPressedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "}");
  }

  /**
   * "this" listener handler, delete single stub.
   */
  public void test_delete_method_interfaceWithConditionalStub_flat() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      if (e.getSource() == this)",
        "        onThis_keyPressed(e);",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    // do delete, no GUI expected
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    keyPressedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "}");
  }

  /**
   * "this" listener handler, delete single stub.<br>
   * Keep stub method, because it is invoked from other places, not only from listener method.
   */
  public void test_delete_method_interfaceWithConditionalStub_plusOtherPlace() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "    // not in listener, so ignored",
        "    onThis_keyPressed(null);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "      if (e.getSource() == this)",
        "        onThis_keyPressed(e);",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
    // do delete, no GUI expected
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    keyPressedProperty.setValue(Property.UNKNOWN_VALUE);
    assertEditor(
        "class Test extends JPanel implements KeyListener {",
        "  Test() {",
        "    addKeyListener(this);",
        "    // not in listener, so ignored",
        "    onThis_keyPressed(null);",
        "  }",
        "    public void keyPressed(KeyEvent e) {",
        "    }",
        "    public void keyReleased(KeyEvent e) {",
        "    }",
        "    public void keyTyped(KeyEvent e) {",
        "    }",
        "    private void onThis_keyPressed(KeyEvent e) {",
        "    }",
        "}");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Decoration
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Test that icon of components with event handlers is decorated.
   */
  public void test_decorateIcon() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    {",
        "      JButton button_1 = new JButton();",
        "      add(button_1);",
        "      button_1.addKeyListener(new KeyAdapter() {",
        "      });",
        "    }",
        "    {",
        "      JButton button_2 = new JButton();",
        "      add(button_2);",
        "    }",
        "  }",
        "}");
    ComponentInfo button_1 = panel.getChildrenComponents().get(0);
    ComponentInfo button_2 = panel.getChildrenComponents().get(1);
    // be default decoration enabled
    assertNotSame(
        button_1.getPresentation().getIcon(),
        ObjectsLabelProvider.INSTANCE.getImage(button_1));
    assertSame(
        button_2.getPresentation().getIcon(),
        ObjectsLabelProvider.INSTANCE.getImage(button_2));
    // disable decoration, no decoration expected
    panel.getDescription().getToolkit().getPreferences().setValue(P_DECORATE_ICON, false);
    assertSame(
        button_1.getPresentation().getIcon(),
        ObjectsLabelProvider.INSTANCE.getImage(button_1));
    assertSame(
        button_2.getPresentation().getIcon(),
        ObjectsLabelProvider.INSTANCE.getImage(button_2));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // ListenerMethodPropertyEditor
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_ListenerMethodPropertyEditor_doubleClick() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyReleased(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    DesignPageSite.Helper.setSite(panel, DesignPageSite.EMPTY);
    //
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    Property keyReleasedProperty = getEventsListenerMethod(panel, "key", "released");
    assertNull(getPropertyText(keyPressedProperty));
    assertEquals("line 8", getPropertyText(keyReleasedProperty));
    // open "keyPressed" method
    {
      PropertyEditor keyPressedEditor = keyPressedProperty.getEditor();
      ReflectionUtils.invokeMethod(
          keyPressedEditor,
          "doubleClick(" + Property.class.getName() + ",org.eclipse.swt.graphics.Point)",
          new Object[]{keyPressedProperty, null});
      assertEditor(
          "class Test extends JPanel {",
          "  Test() {",
          "    addKeyListener(new KeyAdapter() {",
          "      public void keyReleased(KeyEvent e) {",
          "      }",
          "      @Override",
          "      public void keyPressed(KeyEvent e) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  /**
   * Create listener method using
   * {@link PropertyEditor#activate(PropertyTable, Property, org.eclipse.swt.graphics.Point)}.
   */
  public void test_ListenerMethodPropertyEditor_activate() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "    });",
        "  }",
        "}");
    DesignPageSite.Helper.setSite(panel, DesignPageSite.EMPTY);
    //
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    assertNull(getPropertyText(keyPressedProperty));
    // open "keyPressed" method
    {
      PropertyEditor keyPressedEditor = keyPressedProperty.getEditor();
      ReflectionUtils.invokeMethod(
          keyPressedEditor,
          "activate("
              + PropertyTable.class.getName()
              + ","
              + Property.class.getName()
              + ",org.eclipse.swt.graphics.Point)",
          new Object[]{null, keyPressedProperty, null});
      assertEditor(
          "class Test extends JPanel {",
          "  Test() {",
          "    addKeyListener(new KeyAdapter() {",
          "      @Override",
          "      public void keyPressed(KeyEvent e) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Special cases
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * "Handler" suffix for addX[Listener/Handler]() methods.
   */
  public void test_addXHandler() throws Exception {
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyCoolHandler(KeyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    EventsProperty eventsProperty = (EventsProperty) panel.getPropertyByTitle("Events");
    Property myCoolProperty = getPropertyByTitle(getSubProperties(eventsProperty), "myCool");
    assertNotNull(myCoolProperty);
  }

  /**
   * ExtGWT uses classes (not interfaces) as listener parameter. We check that this is supported.
   */
  public void test_listenerAsClass() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener implements java.util.EventListener {",
            "  public abstract void handle(KeyEvent event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(panel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "    addMyListener(new MyListener() {",
        "      public void handle(KeyEvent event) {",
        "      }",
        "    });",
        "  }",
        "}");
  }

  /**
   * ExtGWT uses classes (not interfaces) as listener parameter. When "inner type" code generation.
   */
  public void test_listenerAsClass_innerType() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener implements java.util.EventListener {",
            "  public abstract void handle(KeyEvent event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // set preferences
    IPreferenceStore preferences = panel.getDescription().getToolkit().getPreferences();
    preferences.setValue(P_CODE_TYPE, V_CODE_INNER_CLASS);
    preferences.setValue(P_INNER_POSITION, V_INNER_FIRST);
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  private class ThisMyListener extends MyListener {",
        "    public void handle(KeyEvent event) {",
        "    }",
        "  }",
        "  public Test() {",
        "    addMyListener(new ThisMyListener());",
        "  }",
        "}");
  }

  /**
   * ExtGWT uses non-abstract methods in class as listener methods, for example
   * <code>WidgetListener</code>.
   */
  public void test_listenerAsClass_useDeclaredNonAbstractMethods() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener implements java.util.EventListener {",
            "  public void handle(KeyEvent event) {",
            "  }",
            "  public void handle2(KeyEvent event) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(panel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(2);
      assertEquals("handle", methods[0].getTitle());
      assertEquals("handle2", methods[1].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "    addMyListener(new MyListener() {",
        "      public void handle(KeyEvent event) {",
        "      }",
        "    });",
        "  }",
        "}");
  }

  /**
   * Test that implementation of generic interface does not cause duplicate methods.
   */
  public void test_listenerAsClass_implementGenericInterface() throws Exception {
    setFileContentSrc(
        "test/MyInterface.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public interface MyInterface<E> {",
            "  void handle(E event);",
            "}"));
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener implements MyInterface<KeyEvent> {",
            "  public void handle(KeyEvent event) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(panel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
  }

  /**
   * ExtGWT uses listener classes with generics.
   */
  public void test_listenerWithGeneric() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E extends java.awt.event.ComponentEvent> {",
            "  public abstract void handle(E event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener<KeyEvent> listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(panel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "    addMyListener(new MyListener<KeyEvent>() {",
        "      public void handle(KeyEvent event) {",
        "      }",
        "    });",
        "  }",
        "}");
  }

  /**
   * ExtGWT uses listener classes with generics.
   */
  public void test_listenerWithGeneric_innerType() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E extends java.awt.event.ComponentEvent> {",
            "  public abstract void handle(E event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener<KeyEvent> listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // set preferences
    IPreferenceStore preferences = panel.getDescription().getToolkit().getPreferences();
    preferences.setValue(P_CODE_TYPE, V_CODE_INNER_CLASS);
    preferences.setValue(P_INNER_POSITION, V_INNER_FIRST);
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  private class ThisMyListener extends MyListener<KeyEvent> {",
        "    public void handle(KeyEvent event) {",
        "    }",
        "  }",
        "  public Test() {",
        "    addMyListener(new ThisMyListener());",
        "  }",
        "}");
  }

  /**
   * In GWT <code>DatePicker</code> uses parameterized <code>ValueChangeHandler</code> with
   * parameterized (again!) <code>ValueChangeEvent</code>. So, we should resolve type parameters
   * deeply.
   */
  public void test_listenerWithGeneric_parameterizedEvent() throws Exception {
    setFileContentSrc(
        "test/MyEvent.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public abstract class MyEvent<E> {",
            "  public E value;",
            "}"));
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E> {",
            "  public abstract void handle(MyEvent<E> event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener<String> listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(panel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(panel, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "    addMyListener(new MyListener<String>() {",
        "      public void handle(MyEvent<String> event) {",
        "      }",
        "    });",
        "  }",
        "}");
  }

  /**
   * <code>Listener</code> has parameter for <code>Event</code> and value of this type parameter is
   * specified in component creation.
   */
  public void test_listenerWithGeneric_parameterizedEvent_actualInType() throws Exception {
    setFileContentSrc(
        "test/MyEvent.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public abstract class MyEvent<E> {",
            "  public E value;",
            "}"));
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E> {",
            "  public abstract void handle(MyEvent<E> event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel<D> extends JPanel {",
            "  public void addMyListener(MyListener<D> listener) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel2.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "// filler filler filler",
            "public class MyPanel2<D2> extends MyPanel<D2> {",
            "}"));
    waitForAutoBuild();
    // parse
    parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel2<String> inner = new MyPanel2<String>();",
        "      add(inner);",
        "    }",
        "  }",
        "}");
    ContainerInfo inner = getJavaInfoByName("inner");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(inner, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(inner, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel2<String> inner = new MyPanel2<String>();",
        "      inner.addMyListener(new MyListener<String>() {",
        "        public void handle(MyEvent<String> event) {",
        "        }",
        "      });",
        "      add(inner);",
        "    }",
        "  }",
        "}");
  }

  /**
   * <code>Listener</code> has parameter for <code>Event</code> and value of this type parameter is
   * specified in one of the superclasses.
   */
  public void test_listenerWithGeneric_parameterizedEvent_actualInSuperclass() throws Exception {
    setFileContentSrc(
        "test/MyEvent.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public abstract class MyEvent<E> {",
            "  public E value;",
            "}"));
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "// filler filler filler filler filler",
            "public abstract class MyListener<E> {",
            "  public abstract void handle(MyEvent<E> event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "// filler filler filler filler filler",
            "public class MyPanel<D> extends JPanel {",
            "  public void addMyListener(MyListener<D> listener) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel2.java",
        getTestSource(
            "// filler filler filler filler filler",
            "public class MyPanel2<D2> extends MyPanel<D2> {",
            "}"));
    setFileContentSrc(
        "test/MyPanel3.java",
        getTestSource(
            "// filler filler filler filler filler",
            "public class MyPanel3<D3> extends MyPanel2<D3> {",
            "}"));
    setFileContentSrc(
        "test/MyPanel4.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public class MyPanel4 extends MyPanel3<String> {",
            "}"));
    setFileContentSrc(
        "test/MyPanel5.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public class MyPanel5 extends MyPanel4 {",
            "}"));
    waitForAutoBuild();
    // parse
    parseContainer(
        "// filler filler filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel5 inner = new MyPanel5();",
        "      add(inner);",
        "    }",
        "  }",
        "}");
    ContainerInfo inner = getJavaInfoByName("inner");
    // open "my.handle" listener method
    ensureListenerMethod(inner, "my", "handle");
    assertEditor(
        "// filler filler filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel5 inner = new MyPanel5();",
        "      inner.addMyListener(new MyListener<String>() {",
        "        public void handle(MyEvent<String> event) {",
        "        }",
        "      });",
        "      add(inner);",
        "    }",
        "  }",
        "}");
  }

  /**
   * Test for case when value for type parameter is not specified in {@link ClassInstanceCreation}.
   */
  public void test_listenerWithGeneric_parameterizedEvent_noTypeArgument() throws Exception {
    setFileContentSrc(
        "test/MyEvent.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public abstract class MyEvent<E> {",
            "  public E value;",
            "}"));
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E> {",
            "  public abstract void handle(MyEvent<E> event);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel<D> extends JPanel {",
            "  public void addMyListener(MyListener<D> listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    parseContainer(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel inner = new MyPanel();",
        "      add(inner);",
        "    }",
        "  }",
        "}");
    ContainerInfo inner = getJavaInfoByName("inner");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(inner, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(inner, "my", "handle");
    assertEditor(
        "// filler filler filler",
        "public class Test extends JPanel {",
        "  public Test() {",
        "    {",
        "      MyPanel inner = new MyPanel();",
        "      inner.addMyListener(new MyListener<Object>() {",
        "        public void handle(MyEvent<Object> event) {",
        "        }",
        "      });",
        "      add(inner);",
        "    }",
        "  }",
        "}");
  }

  /**
   * Case when we add listener to for the generic component, and type argument is type parameter of
   * form which we parse now. So this type argument can not be resolved to an actual {@link Class}.
   */
  public void test_listenerWithGeneric_pureTypeVariable() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "public abstract class MyListener<E> {",
            "  public abstract void handle(E object);",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel<T> extends JPanel {",
            "  public void addMyListener(MyListener<T> listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    parseContainer(
        "// filler filler filler filler filler",
        "public class Test<T> extends JPanel {",
        "  public Test() {",
        "    MyPanel<T> myPanel = new MyPanel<T>();",
        "    add(myPanel);",
        "  }",
        "}");
    ContainerInfo myPanel = getJavaInfoByName("myPanel");
    // check that "my" listener exists and has only "handle" method
    {
      Property listener = getEventsListener(myPanel, "my");
      assertNotNull(listener);
      Property[] methods = getSubProperties(listener);
      assertThat(methods).hasSize(1);
      assertEquals("handle", methods[0].getTitle());
    }
    // open "my.handle" listener method
    ensureListenerMethod(myPanel, "my", "handle");
    assertEditor(
        "// filler filler filler filler filler",
        "public class Test<T> extends JPanel {",
        "  public Test() {",
        "    MyPanel<T> myPanel = new MyPanel<T>();",
        "    myPanel.addMyListener(new MyListener<T>() {",
        "      public void handle(T object) {",
        "      }",
        "    });",
        "    add(myPanel);",
        "  }",
        "}");
  }

  /**
   * Listener that has name "addListener".
   */
  public void test_addListener_justSuchName() throws Exception {
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addListener(KeyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // there is "addListener" property
    {
      Property listenerProperty = getEventsListener(panel, "addListener");
      assertNotNull(listenerProperty);
    }
    // we can use it to open some listener method
    {
      Property pressedProperty = getEventsListenerMethod(panel, "addListener", "keyPressed");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  public Test() {",
          "    addListener(new KeyAdapter() {",
          "      @Override",
          "      public void keyPressed(KeyEvent e) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  /**
   * "Adapter" that is constructed by adding word "Adapter" to the "MyListener" name.
   */
  public void test_addMyListenerAdapter() throws Exception {
    setFileContentSrc(
        "test/MyListener.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public interface MyListener {",
            "  void down();",
            "  void up();",
            "}"));
    setFileContentSrc(
        "test/MyListenerAdapter.java",
        getTestSource(
            "public class MyListenerAdapter implements MyListener {",
            "  public void down() {",
            "  }",
            "  public void up() {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // open listener, adapter should be used
    {
      Property pressedProperty = getEventsListenerMethod(panel, "my", "down");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  public Test() {",
          "    addMyListener(new MyListenerAdapter() {",
          "      @Override",
          "      public void down() {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  /**
   * Test for case when there are inheritance for listeners, so also inheritance for adapters.
   */
  public void test_addAdapterInheritance() throws Exception {
    setFileContentSrc(
        "test/SuperListener.java",
        getTestSource(
            "// filler filler filler filler filler",
            "// filler filler filler filler filler",
            "public interface SuperListener {",
            "  void foo(int fooValue);",
            "}"));
    setFileContentSrc(
        "test/SubListener.java",
        getTestSource(
            "public interface SubListener extends SuperListener {",
            "  void bar(int barValue);",
            "}"));
    setFileContentSrc(
        "test/SuperListenerAdapter.java",
        getTestSource(
            "public class SuperListenerAdapter implements SuperListener {",
            "  public void foo(int fooValue) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/SubListenerAdapter.java",
        getTestSource(
            "public class SubListenerAdapter extends SuperListenerAdapter implements SuperListener {",
            "  public void bar(int barValue) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public void addSubListener(SubListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // open listener, adapter should be used
    {
      Property pressedProperty = getEventsListenerMethod(panel, "sub", "foo");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  public Test() {",
          "    addSubListener(new SubListenerAdapter() {",
          "      @Override",
          "      public void foo(int fooValue) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Listener as inner type of component
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * When listener is defined not as top level, but as inner type of component, it has
   * <code>$</code> in its name, but we should use its "source" name.
   */
  public void test_listenerAsInnerTypeOfComponent_anonymous() throws Exception {
    prepare_listenerAsInnerTypeOfComponent();
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // open listener
    {
      Property pressedProperty = getEventsListenerMethod(panel, "my", "handle");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "import test.MyPanel.MyListener;",
          "import test.MyPanel.MyEvent;",
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  public Test() {",
          "    addMyListener(new MyListener() {",
          "      public void handle(MyEvent event) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
  }

  /**
   * When listener is defined not as top level, but as inner type of component, it has
   * <code>$</code> in its name, but we should use its "source" name.
   */
  public void test_listenerAsInnerTypeOfComponent_inner() throws Exception {
    prepare_listenerAsInnerTypeOfComponent();
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // set preferences
    IPreferenceStore preferences = panel.getDescription().getToolkit().getPreferences();
    preferences.setValue(P_CODE_TYPE, V_CODE_INNER_CLASS);
    preferences.setValue(P_INNER_POSITION, V_INNER_FIRST);
    // open listener
    {
      Property pressedProperty = getEventsListenerMethod(panel, "my", "handle");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "import test.MyPanel.MyListener;",
          "import test.MyPanel.MyEvent;",
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  private class ThisMyListener implements MyListener {",
          "    public void handle(MyEvent event) {",
          "    }",
          "  }",
          "  public Test() {",
          "    addMyListener(new ThisMyListener());",
          "  }",
          "}");
    }
  }

  /**
   * When listener is defined not as top level, but as inner type of component, it has
   * <code>$</code> in its name, but we should use its "source" name.
   */
  public void test_listenerAsInnerTypeOfComponent_innerAdapter() throws Exception {
    prepare_listenerAsInnerTypeOfComponent_withAdapter();
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // set preferences
    IPreferenceStore preferences = panel.getDescription().getToolkit().getPreferences();
    preferences.setValue(P_CODE_TYPE, V_CODE_INNER_CLASS);
    preferences.setValue(P_INNER_POSITION, V_INNER_FIRST);
    // open listener
    {
      Property pressedProperty = getEventsListenerMethod(panel, "my", "handle");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "import test.MyPanel.MyAdapter;",
          "import test.MyPanel.MyEvent;",
          "// filler filler filler",
          "public class Test extends MyPanel {",
          "  private class ThisMyListener extends MyAdapter {",
          "    public void handle(MyEvent event) {",
          "    }",
          "  }",
          "  public Test() {",
          "    addMyListener(new ThisMyListener());",
          "  }",
          "}");
    }
  }

  /**
   * When listener is defined not as top level, but as inner type of component, it has
   * <code>$</code> in its name, but we should use its "source" name.
   */
  public void test_listenerAsInnerTypeOfComponent_interface() throws Exception {
    prepare_listenerAsInnerTypeOfComponent();
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    // set preferences
    IPreferenceStore preferences = panel.getDescription().getToolkit().getPreferences();
    preferences.setValue(P_CODE_TYPE, V_CODE_INTERFACE);
    // open listener
    {
      Property pressedProperty = getEventsListenerMethod(panel, "my", "handle");
      ReflectionUtils.invokeMethod(pressedProperty, "ensureListenerMethod()");
      assertEditor(
          "import test.MyPanel.MyListener;",
          "import test.MyPanel.MyEvent;",
          "// filler filler filler",
          "public class Test extends MyPanel implements MyListener {",
          "  public Test() {",
          "    addMyListener(this);",
          "  }",
          "  public void handle(MyEvent event) {",
          "  }",
          "}");
    }
  }

  private void prepare_listenerAsInnerTypeOfComponent() throws Exception {
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public class MyEvent {",
            "  }",
            "  public interface MyListener {",
            "    void handle(MyEvent event);",
            "  }",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
  }

  private void prepare_listenerAsInnerTypeOfComponent_withAdapter() throws Exception {
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  public class MyEvent {",
            "  }",
            "  public interface MyListener {",
            "    void handle(MyEvent event);",
            "  }",
            "  public class MyAdapter implements MyListener {",
            "    public void handle(MyEvent event) {",
            "    }",
            "  }",
            "  public void addMyListener(MyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Context menu
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_understand() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    this.addKeyListener(new KeyAdapter() {",
        "      public void keyReleased(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    DesignPageSite.Helper.setSite(panel, DesignPageSite.EMPTY);
    // prepare properties
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    Property keyReleasedProperty = getEventsListenerMethod(panel, "key", "released");
    assertNull(getPropertyText(keyPressedProperty));
    assertEquals("line 8", getPropertyText(keyReleasedProperty));
  }

  public void test_contextMenu() throws Exception {
    ContainerInfo panel = parseContainer(
        "class Test extends JPanel {",
        "  Test() {",
        "    addKeyListener(new KeyAdapter() {",
        "      public void keyReleased(KeyEvent e) {",
        "      }",
        "    });",
        "  }",
        "}");
    DesignPageSite.Helper.setSite(panel, DesignPageSite.EMPTY);
    // prepare properties
    Property keyPressedProperty = getEventsListenerMethod(panel, "key", "pressed");
    Property keyReleasedProperty = getEventsListenerMethod(panel, "key", "released");
    assertNull(getPropertyText(keyPressedProperty));
    assertEquals("line 8", getPropertyText(keyReleasedProperty));
    // prepare context menu
    IMenuManager manager;
    {
      manager = getDesignerMenuManager();
      panel.getBroadcastObject().addContextMenu(null, panel, manager);
    }
    // check action for existing "keyReleased" event
    {
      IAction keyReleasedAction = findChildAction(manager, "keyReleased -> line 8");
      assertNotNull(keyReleasedAction);
      assertSame(
          EventsPropertyUtils.LISTENER_METHOD_IMAGE_DESCRIPTOR,
          keyReleasedAction.getImageDescriptor());
      // run, no change expected
      String expectedSource = m_lastEditor.getSource();
      keyReleasedAction.run();
      assertEditor(expectedSource, m_lastEditor);
    }
    // add new handler using action
    {
      IMenuManager manager2 = findChildMenuManager(manager, "Add event handler");
      manager2 = findChildMenuManager(manager2, "key");
      assertSame(EventsPropertyUtils.EXISTING_CLASS_IMAGE, ((MenuManagerEx) manager2).getImage());
      //
      IAction keyPressedAction = findChildAction(manager2, "keyPressed");
      assertNotNull(keyPressedAction);
      // run, new handler should be added
      keyPressedAction.run();
      assertEditor(
          "class Test extends JPanel {",
          "  Test() {",
          "    addKeyListener(new KeyAdapter() {",
          "      public void keyReleased(KeyEvent e) {",
          "      }",
          "      @Override",
          "      public void keyPressed(KeyEvent e) {",
          "      }",
          "    });",
          "  }",
          "}");
    }
    // check for images
    {
      IMenuManager manager2 = findChildMenuManager(manager, "Add event handler");
      assertSame(
          EventsPropertyUtils.LISTENER_INTERFACE_IMAGE,
          ((MenuManagerEx) findChildMenuManager(manager2, "ancestor")).getImage());
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Deprecated
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_deprecatedListenerMethod() throws Exception {
    setFileContentSrc(
        "test/MyPanel.java",
        getTestSource(
            "public class MyPanel extends JPanel {",
            "  @Deprecated",
            "  public void addMyListener(KeyListener listener) {",
            "  }",
            "}"));
    waitForAutoBuild();
    // parse
    ContainerInfo panel = parseContainer(
        "// filler filler filler",
        "public class Test extends MyPanel {",
        "  public Test() {",
        "  }",
        "}");
    Property listener = getEventsListener(panel, "my");
    assertNotNull(listener);
    assertSame(PropertyCategory.ADVANCED, listener.getCategory());
    // "addMyListener" is deprecated, so we don't show it in menu
    {
      IMenuManager panelManager = getContextMenu(panel);
      IMenuManager eventManager = findChildMenuManager(panelManager, "Add event handler");
      IMenuManager myManager = findChildMenuManager(eventManager, "my");
      assertNull(myManager);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  ////////////////////////////////////////////////////////////////////////////
  public static Property[] getEventListeners(JavaInfo javaInfo) throws Exception {
    Property eventsProperty = javaInfo.getPropertyByTitle("Events");
    return getSubProperties(eventsProperty);
  }

  public static Property getEventsListener(JavaInfo javaInfo, String listener) throws Exception {
    Property[] subProperties = getEventListeners(javaInfo);
    return getPropertyByTitle(subProperties, listener);
  }

  public static Property getEventsListenerMethod(JavaInfo javaInfo, String listener, String method)
      throws Exception {
    Property listenerProperty = getEventsListener(javaInfo, listener);
    return getPropertyByTitle(getSubProperties(listenerProperty), method);
  }

  /**
   * Ensures that listener/method with given names are exist in source (create if needed).
   *
   * @return the {@link MethodDeclaration} that method of listener to handle this event.
   */
  public static MethodDeclaration ensureListenerMethod(JavaInfo javaInfo,
      String listener,
      String method) throws Exception {
    Property property = getEventsListenerMethod(javaInfo, listener, method);
    return (MethodDeclaration) ReflectionUtils.invokeMethod(property, "ensureListenerMethod()");
  }
}
