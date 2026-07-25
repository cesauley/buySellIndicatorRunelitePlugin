package com.buysell;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

/**
 * Shared RuneLite-sidebar styling helpers for the Buy/Sell panels.
 */
final class PanelUi
{
    static final Color BUY_COLOR = new Color(0, 220, 80);
    static final Color SELL_COLOR = new Color(255, 128, 128);
    static final Color STATUS_COLOR = new Color(220, 180, 80);
    static final Color CHECKBOX_SELECTED = new Color(45, 126, 235);

    private static final int ICON_BUTTON_SIZE = 30;
    private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;

    private PanelUi()
    {
    }

    static JPanel header(String title, JComponent leading, JComponent trailing)
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setHorizontalAlignment(JLabel.CENTER);

        JPanel west = new JPanel(new BorderLayout());
        west.setBackground(ColorScheme.DARK_GRAY_COLOR);
        west.setPreferredSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
        if (leading != null)
        {
            west.add(leading, BorderLayout.CENTER);
        }

        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(ColorScheme.DARK_GRAY_COLOR);
        east.setPreferredSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
        if (trailing != null)
        {
            east.add(trailing, BorderLayout.CENTER);
        }

        header.add(west, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(east, BorderLayout.EAST);
        return header;
    }

    static JButton iconButton(Icon icon, String tooltip, String accessibleName)
    {
        JButton button = new JButton(icon);
        button.setUI(new BasicButtonUI());
        SwingUtil.removeButtonDecorations(button);
        button.setPreferredSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
        button.setMinimumSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
        button.setMaximumSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(ColorScheme.TEXT_COLOR);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });
        return button;
    }

    static JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.BRAND_ORANGE);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setBorder(new EmptyBorder(4, 0, 6, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    static JLabel mutedLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    static JScrollPane verticalScroll(JComponent content)
    {
        JPanel wrapped = new JPanel(new BorderLayout());
        wrapped.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapped.add(content, BorderLayout.NORTH);
        return configureScroll(new JScrollPane(wrapped));
    }

    /**
     * Scroll pane for a {@link WidthTrackingPanel} form. The form must be the
     * viewport view so {@code getScrollableTracksViewportWidth()} takes effect.
     */
    static JScrollPane formScroll(WidthTrackingPanel form)
    {
        return configureScroll(new JScrollPane(form));
    }

    private static JScrollPane configureScroll(JScrollPane scroll)
    {
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
        scroll.getVerticalScrollBar().setBorder(new EmptyBorder(0, 9, 0, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    static WidthTrackingPanel formColumn()
    {
        WidthTrackingPanel form = new WidthTrackingPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return form;
    }

    static JPanel labeledField(String title, JComponent field)
    {
        styleInput(field);
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(mutedLabel(title), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        int height = field instanceof JScrollPane ? 96 : 54;
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        panel.setPreferredSize(new Dimension(CONTENT_WIDTH, height));
        return panel;
    }

    static JPanel checkboxRow(String title, JCheckBox box)
    {
        styleCheckbox(box);
        box.setText("");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel label = mutedLabel(title);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());

        panel.add(label, BorderLayout.CENTER);
        panel.add(box, BorderLayout.EAST);
        return panel;
    }

    static void styleCheckbox(JCheckBox box)
    {
        box.setBackground(ColorScheme.DARK_GRAY_COLOR);
        box.setForeground(Color.WHITE);
        box.setFocusPainted(false);
        box.setOpaque(false);
        box.setIcon(new CheckboxIcon());
        box.setSelectedIcon(new CheckboxIcon());
        box.setDisabledIcon(new CheckboxIcon());
        box.setDisabledSelectedIcon(new CheckboxIcon());
    }

    static void styleInput(JComponent field)
    {
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setForeground(Color.WHITE);
        field.setFont(FontManager.getRunescapeSmallFont());
        if (field instanceof JSpinner)
        {
            JSpinner spinner = (JSpinner) field;
            JComponent editor = spinner.getEditor();
            editor.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            for (Component child : editor.getComponents())
            {
                child.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                child.setForeground(Color.WHITE);
                if (child instanceof JComponent)
                {
                    ((JComponent) child).setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                }
            }
        }
        else if (field instanceof JComboBox)
        {
            @SuppressWarnings("unchecked")
            JComboBox<Object> combo = (JComboBox<Object>) field;
            combo.setRenderer(new DarkListCellRenderer(combo.getRenderer()));
            combo.setMaximumRowCount(8);
        }
        else if (field instanceof JTextArea)
        {
            JTextArea area = (JTextArea) field;
            area.setCaretColor(Color.WHITE);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setBorder(new EmptyBorder(4, 4, 4, 4));
        }
        else if (field instanceof JScrollPane)
        {
            JScrollPane scroll = (JScrollPane) field;
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
            if (scroll.getViewport().getView() instanceof JComponent)
            {
                styleInput((JComponent) scroll.getViewport().getView());
            }
        }
    }

    static Component strut(int height)
    {
        return Box.createVerticalStrut(height);
    }

    static Icon cogIcon()
    {
        return new CogIcon();
    }

    static Icon backIcon()
    {
        return new BackIcon();
    }

    /**
     * Scrollable column that tracks the viewport width to avoid right-edge clipping.
     */
    static final class WidthTrackingPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static final class DarkListCellRenderer implements ListCellRenderer<Object>
    {
        private final ListCellRenderer<? super Object> delegate;

        private DarkListCellRenderer(ListCellRenderer<? super Object> delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends Object> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            Component c = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            c.setBackground(isSelected ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            c.setForeground(Color.WHITE);
            c.setFont(FontManager.getRunescapeSmallFont());
            return c;
        }
    }

    private static final class CogIcon implements Icon
    {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.isEnabled() ? ColorScheme.TEXT_COLOR : Color.GRAY);
                int centerX = x + SIZE / 2;
                int centerY = y + SIZE / 2;
                for (int angle = 0; angle < 360; angle += 45)
                {
                    g.fill(new Arc2D.Double(centerX - 3, centerY - 8, 6, 6, angle, 30, Arc2D.PIE));
                }
                g.fillOval(centerX - 6, centerY - 6, 12, 12);
                g.setColor(ColorScheme.DARK_GRAY_COLOR);
                g.fillOval(centerX - 2, centerY - 2, 4, 4);
            }
            finally
            {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }

    private static final class BackIcon implements Icon
    {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.isEnabled() ? ColorScheme.TEXT_COLOR : Color.GRAY);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int centerY = y + SIZE / 2;
                g.drawLine(x + 3, centerY, x + 15, centerY);
                g.drawLine(x + 3, centerY, x + 8, centerY - 5);
                g.drawLine(x + 3, centerY, x + 8, centerY + 5);
            }
            finally
            {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }

    private static final class CheckboxIcon implements Icon
    {
        private static final int SIZE = 14;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            JCheckBox box = (JCheckBox) component;
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = box.isSelected();
                boolean enabled = box.isEnabled();
                Color fill = selected ? CHECKBOX_SELECTED : ColorScheme.DARKER_GRAY_COLOR;
                if (!enabled)
                {
                    fill = ColorScheme.MEDIUM_GRAY_COLOR;
                }
                g.setColor(fill);
                g.fillRoundRect(x, y, SIZE - 1, SIZE - 1, 3, 3);
                g.setColor(selected ? CHECKBOX_SELECTED.brighter() : Color.GRAY);
                g.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 3, 3);

                if (selected)
                {
                    Path2D check = new Path2D.Double();
                    check.moveTo(x + 3, y + 7);
                    check.lineTo(x + 6, y + 10);
                    check.lineTo(x + 11, y + 4);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(check);
                }
            }
            finally
            {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }
}
