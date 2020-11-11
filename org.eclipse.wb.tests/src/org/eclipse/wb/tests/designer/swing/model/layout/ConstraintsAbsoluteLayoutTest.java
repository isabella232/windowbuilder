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
package org.eclipse.wb.tests.designer.swing.model.layout;

import org.eclipse.wb.draw2d.geometry.Dimension;
import org.eclipse.wb.draw2d.geometry.Point;
import org.eclipse.wb.internal.core.model.property.Property;
import org.eclipse.wb.internal.core.model.util.PropertyUtils;
import org.eclipse.wb.internal.swing.model.component.ComponentInfo;
import org.eclipse.wb.internal.swing.model.component.ContainerInfo;
import org.eclipse.wb.internal.swing.model.layout.absolute.ConstraintsAbsoluteLayoutDataInfo;
import org.eclipse.wb.internal.swing.model.layout.absolute.ConstraintsAbsoluteLayoutInfo;

/**
 * Tests for {@link ConstraintsAbsoluteLayoutInfo}.
 *
 * @author scheglov_ke
 */
public class ConstraintsAbsoluteLayoutTest extends AbstractLayoutTest {
  ////////////////////////////////////////////////////////////////////////////
  //
  // Life cycle
  //
  ////////////////////////////////////////////////////////////////////////////
  private void createMyLayoutMyConstraints() throws Exception {
    setFileContentSrc(
        "test/MyLayout.java",
        getTestSource(
            "public class MyLayout implements LayoutManager {",
            "  public void addLayoutComponent(String name, Component comp) {",
            "  }",
            "  public  void removeLayoutComponent(Component comp) {",
            "  }",
            "  public Dimension preferredLayoutSize(Container parent) {",
            "    return new Dimension(200, 100);",
            "  }",
            "  public Dimension minimumLayoutSize(Container parent) {",
            "    return new Dimension(200, 100);",
            "  }",
            "  public void layoutContainer(Container parent) {",
            "  }",
            "}"));
    setFileContentSrc(
        "test/MyConstraints.java",
        getTestSource(
            "public class MyConstraints implements Cloneable, java.io.Serializable {",
            "  public MyConstraints(final int x, final int y, final int width, final int height) {",
            "  }",
            "}"));
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
  // Tests
  //
  ////////////////////////////////////////////////////////////////////////////
  public void _test_parse() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(1, 2, 3, 4));",
        "    }",
        "  }",
        "}");
    panel.refresh();
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button, new MyConstraints(1, 2, 3, 4))/}",
        "  {implicit-layout: java.awt.FlowLayout} {implicit-layout} {}",
        "  {new: javax.swing.JButton} {local-unique: button} {/new JButton()/ /add(button, new MyConstraints(1, 2, 3, 4))/}");
    //
    ComponentInfo button = panel.getChildrenComponents().get(0);
    {
      Property boundsProperty = button.getPropertyByTitle("Bounds");
      assertEquals("(1, 2, 3, 4)", getPropertyText(boundsProperty));
      assertBoundsSubProperty_getValue(button, "x", 1);
      assertBoundsSubProperty_getValue(button, "y", 2);
      assertBoundsSubProperty_getValue(button, "width", 3);
      assertBoundsSubProperty_getValue(button, "height", 4);
    }
  }

  private static void assertBoundsSubProperty_getValue(ComponentInfo component,
      String name,
      int expected) throws Exception {
    Property property = PropertyUtils.getByPath(component, "Bounds/" + name);
    assertNotNull(property);
    assertEquals(expected, property.getValue());
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Constraints
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Test for {@link ConstraintsAbsoluteLayoutInfo#getConstraints(ComponentInfo)}.
   */
  public void _test_getConstraints_existing() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(1, 2, 3, 4));",
        "    }",
        "  }",
        "}");
    panel.refresh();
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button, new MyConstraints(1, 2, 3, 4))/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}",
        "  {new: javax.swing.JButton} {local-unique: button} {/new JButton()/ /add(button, new MyConstraints(1, 2, 3, 4))/}",
        "    {new: test.MyConstraints} {empty} {/add(button, new MyConstraints(1, 2, 3, 4))/}");
    String expectedSource = m_lastEditor.getSource();
    //
    ComponentInfo button = panel.getChildrenComponents().get(0);
    ConstraintsAbsoluteLayoutDataInfo constraints =
        ConstraintsAbsoluteLayoutInfo.getConstraints(button);
    assertNotNull(constraints);
    // source not changed
    assertEditor(expectedSource, m_lastEditor);
  }

  /**
   * Test for {@link ConstraintsAbsoluteLayoutInfo#getConstraints(ComponentInfo)}.
   */
  public void _test_getConstraints_new() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "    }",
        "  }",
        "}");
    panel.refresh();
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button)/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}",
        "  {new: javax.swing.JButton} {local-unique: button} {/new JButton()/ /add(button)/}");
    //
    ComponentInfo button = panel.getChildrenComponents().get(0);
    ConstraintsAbsoluteLayoutDataInfo constraints =
        ConstraintsAbsoluteLayoutInfo.getConstraints(button);
    assertNotNull(constraints);
    // source changed
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(0, 0, 0, 0));",
        "    }",
        "  }",
        "}");
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button, new MyConstraints(0, 0, 0, 0))/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}",
        "  {new: javax.swing.JButton} {local-unique: button} {/new JButton()/ /add(button, new MyConstraints(0, 0, 0, 0))/}",
        "    {new: test.MyConstraints} {empty} {/add(button, new MyConstraints(0, 0, 0, 0))/}");
  }

  /**
   * {@link ConstraintsAbsoluteLayoutDataInfo} should not be displayed in components tree.
   */
  public void _test_Constraints_isNotVisibleInTree() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(1, 2, 3, 4));",
        "    }",
        "  }",
        "}");
    ComponentInfo button = panel.getChildrenComponents().get(0);
    ConstraintsAbsoluteLayoutDataInfo constraints =
        ConstraintsAbsoluteLayoutInfo.getConstraints(button);
    //
    assertVisibleInTree(constraints, false);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Commands
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Test for {@link ConstraintsAbsoluteLayoutInfo#command_CREATE(ComponentInfo, ComponentInfo)}.
   */
  public void _test_CREATE() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "  }",
        "}");
    panel.refresh();
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}");
    //
    ConstraintsAbsoluteLayoutInfo layout = (ConstraintsAbsoluteLayoutInfo) panel.getLayout();
    ComponentInfo newButton = createJButton();
    layout.command_CREATE(newButton, null);
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button);",
        "    }",
        "  }",
        "}");
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button)/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}",
        "  {new: javax.swing.JButton empty} {local-unique: button} {/new JButton()/ /add(button)/}");
    // set bounds
    layout.command_BOUNDS(newButton, new Point(1, 2), new Dimension(3, 4));
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(1, 2, 3, 4));",
        "    }",
        "  }",
        "}");
    assertHierarchy(
        "{this: javax.swing.JPanel} {this} {/setLayout(new MyLayout())/ /add(button, new MyConstraints(1, 2, 3, 4))/}",
        "  {new: test.MyLayout} {empty} {/setLayout(new MyLayout())/}",
        "  {new: javax.swing.JButton empty} {local-unique: button} {/new JButton()/ /add(button, new MyConstraints(1, 2, 3, 4))/}",
        "    {new: test.MyConstraints} {empty} {/add(button, new MyConstraints(1, 2, 3, 4))/}");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Bounds
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * When set "width" to preferred size, then <code>0</code> should be used.
   */
  public void _test_setWidth_preferred() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(10, 20, 100, 50));",
        "    }",
        "  }",
        "}");
    panel.refresh();
    //
    ComponentInfo button = panel.getChildrenComponents().get(0);
    Property property = PropertyUtils.getByPath(button, "Bounds/width");
    property.setValue(button.getPreferredSize().width);
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(10, 20, 0, 50));",
        "    }",
        "  }",
        "}");
  }

  /**
   * When set "height" to preferred size, then <code>0</code> should be used.
   */
  public void _test_setHeight_preferred() throws Exception {
    createMyLayoutMyConstraints();
    ContainerInfo panel = parseContainer(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(10, 20, 100, 50));",
        "    }",
        "  }",
        "}");
    panel.refresh();
    //
    ComponentInfo button = panel.getChildrenComponents().get(0);
    Property property = PropertyUtils.getByPath(button, "Bounds/height");
    property.setValue(button.getPreferredSize().height);
    assertEditor(
        "public class Test extends JPanel {",
        "  public Test() {",
        "    setLayout(new MyLayout());",
        "    {",
        "      JButton button = new JButton();",
        "      add(button, new MyConstraints(10, 20, 100, 0));",
        "    }",
        "  }",
        "}");
  }
}
