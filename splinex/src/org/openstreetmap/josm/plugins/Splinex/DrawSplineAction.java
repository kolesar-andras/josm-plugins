// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.gui.util.ModifierExListener;
import org.openstreetmap.josm.plugins.Splinex.Spline.PointHandle;
import org.openstreetmap.josm.plugins.Splinex.Spline.SplinePoint;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

@SuppressWarnings("serial")
public class DrawSplineAction extends MapMode implements MapViewPaintable, KeyPressReleaseListener, ModifierExListener,
        LayerChangeListener, ActiveLayerChangeListener, DataSetListener {
    private final Cursor cursorJoinNode;
    private final Cursor cursorJoinWay;

    private Color rubberLineColor;

    private final Shortcut backspaceShortcut;
    private final BackSpaceAction backspaceAction;

    boolean drawHelperLine;

    public DrawSplineAction(MapFrame mapFrame) {
        super(tr("Spline drawing"), // name
                "spline2", // icon name
                tr("Draw a spline curve"), // tooltip
                Shortcut.registerShortcut("mapmode:spline",
                        tr("Mode: {0}", tr("Spline drawing")),
                        KeyEvent.VK_L, Shortcut.DIRECT),
                getCursor());

        backspaceShortcut = Shortcut.registerShortcut("mapmode:backspace", tr("Backspace in Add mode"),
                KeyEvent.VK_BACK_SPACE, Shortcut.DIRECT);
        backspaceAction = new BackSpaceAction();
        cursorJoinNode = ImageProvider.getCursor("crosshair", "joinnode");
        cursorJoinWay = ImageProvider.getCursor("crosshair", "joinway");
        MainApplication.getLayerManager().addLayerChangeListener(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
        MapFrame map = MainApplication.getMap();
        map.mapView.addTemporaryLayer(this);
        readPreferences();
    }

    private static Cursor getCursor() {
        try {
            return ImageProvider.getCursor("crosshair", "spline");
        } catch (Exception e) {
            Logging.error(e);
        }
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    @Override
    public void enterMode() {
        if (!isEnabled())
            return;
        super.enterMode();

        MainApplication.registerActionShortcut(backspaceAction, backspaceShortcut);

        MapFrame map = MainApplication.getMap();
        map.mapView.addMouseListener(this);
        map.mapView.addMouseMotionListener(this);
        map.keyDetector.addModifierExListener(this);
        map.keyDetector.addKeyListener(this);
        getLayerManager().getEditLayer().getDataSet().addDataSetListener(this);
        createSplineFromSelection();
    }

    int initialMoveDelay, initialMoveThreshold;

    @Override
    protected void readPreferences() {
        rubberLineColor = new NamedColorProperty(marktr("helper line"), Color.RED).get();
        initialMoveDelay = Config.getPref().getInt("edit.initial-move-", 200);
        initialMoveThreshold = Config.getPref().getInt("edit.initial-move-threshold", 5);
        initialMoveThreshold *= initialMoveThreshold;
        drawHelperLine = Config.getPref().getBoolean("draw.helper-line", true);
    }

    @Override
    public void exitMode() {
        super.exitMode();
        MapFrame map = MainApplication.getMap();
        map.mapView.removeMouseListener(this);
        map.mapView.removeMouseMotionListener(this);
        MainApplication.unregisterActionShortcut(backspaceAction, backspaceShortcut);

        map.statusLine.activateAnglePanel(false);
        map.keyDetector.removeModifierExListener(this);
        map.keyDetector.removeKeyListener(this);
        removeHighlighting();
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void modifiersExChanged(int modifiers) {
        updateKeyModifiersEx(modifiers);
    }

    private Long mouseDownTime;
    private PointHandle ph;
    private Point helperEndpoint;
    private Point clickPos;
    public int index = 0;
    boolean lockCounterpart;
    boolean lockCounterpartLength;
    private MoveCommand mc;
    private boolean dragControl;
    private boolean dragSpline;

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDownTime = null;
        updateKeyModifiers(e);
        if (e.getButton() != MouseEvent.BUTTON1) {
            helperEndpoint = null; // Hide helper line when panning
            return;
        }
        if (!MainApplication.getMap().mapView.isActiveLayerDrawable()) return;
        Spline spl = getSpline();
        if (spl == null) return;
        helperEndpoint = null;
        dragControl = false;
        dragSpline = false;
        mouseDownTime = System.currentTimeMillis();
        ph = spl.getNearestPoint(MainApplication.getMap().mapView, e.getPoint());
        if (e.getClickCount() == 2) {
            if (!spl.isClosed() && spl.nodeCount() > 1 && ph != null && ph.point == SplinePoint.ENDPOINT
                    && ((ph.idx == 0 && direction == 1) || (ph.idx == spl.nodeCount() - 1 && direction == -1))) {
                UndoRedoHandler.getInstance().add(spl.new CloseSplineCommand());
                return;
            }
            spl.finishSpline();
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        clickPos = e.getPoint();
        if (ph != null) {
            if (ctrl) {
                if (ph.point == SplinePoint.ENDPOINT) {
                    ph = ph.otherPoint(SplinePoint.CONTROL_NEXT);
                    lockCounterpart = true;
                    lockCounterpartLength = true;
                } else
                    lockCounterpart = false;
            } else if (ph.point != SplinePoint.ENDPOINT) {
                lockCounterpart =
                        // TODO handle turnover at north
                        Math.abs(ph.sn.cprev.heading(EastNorth.ZERO) - EastNorth.ZERO.heading(ph.sn.cnext)) < 5/180.0*Math.PI;
                lockCounterpartLength =
                        Math.min(ph.sn.cprev.length(), ph.sn.cnext.length()) /
                        Math.max(ph.sn.cprev.length(), ph.sn.cnext.length()) > 0.95;
            }
            if (ph.point == SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
                Command cmd = UndoRedoHandler.getInstance().getLastCommand();
                if (cmd instanceof MoveCommand) {
                    mc = (MoveCommand) cmd;
                    Collection<Node> pp = mc.getParticipatingPrimitives();
                    if (pp.size() != 1 || !pp.contains(ph.sn.node))
                        mc = null;
                    else
                        mc.changeStartPoint(ph.sn.node.getEastNorth());
                }
            }
            if (ph.point != SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
                Command cmd = UndoRedoHandler.getInstance().getLastCommand();
                if (!(cmd instanceof Spline.EditSplineCommand && ((Spline.EditSplineCommand) cmd).sn == ph.sn))
                    dragControl = true;
            }
            return;
        }
        if (!ctrl && spl.doesHit(e.getX(), e.getY(), MainApplication.getMap().mapView)) {
            dragSpline = true;
            return;
        }
        if (spl.isClosed()) return;
        if (direction == 0) {
            if (spl.nodeCount() < 2) {
                direction = 1;
            } else {
                return;
            }
        }
        Node n = null;
        boolean existing = false;
        if (!ctrl) {
            n = MainApplication.getMap().mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
            existing = true;
        }
        if (n == null) {
            n = new Node(MainApplication.getMap().mapView.getLatLon(e.getX(), e.getY()));
            existing = false;
        }
        int idx = direction == -1 ? 0 : spl.nodeCount();
        UndoRedoHandler.getInstance().add(spl.new AddSplineNodeCommand(new Spline.SNode(n), existing, idx));
        ph = spl.new PointHandle(idx, direction == -1 ? SplinePoint.CONTROL_PREV : SplinePoint.CONTROL_NEXT);
        lockCounterpart = true;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mc = null;
        mouseDownTime = null;
        dragSpline = false;
        clickPos = null;
        mouseMoved(e);
        if (direction == 0 && ph != null) {
            if (ph.idx >= ph.getSpline().nodeCount() - 1)
                direction = 1;
            else if (ph.idx == 0)
                direction = -1;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateKeyModifiers(e);
        if (mouseDownTime == null) return;
        if (!MainApplication.getMap().mapView.isActiveLayerDrawable()) return;
        if (System.currentTimeMillis() - mouseDownTime < initialMoveDelay) return;
        Spline spl = getSpline();
        if (spl == null) return;
        if (spl.isEmpty()) return;
        if (clickPos != null && clickPos.distanceSq(e.getPoint()) < initialMoveThreshold)
            return;
        EastNorth en = MainApplication.getMap().mapView.getEastNorth(e.getX(), e.getY());
        if (new Node(en).isOutSideWorld())
            return;
        if (dragSpline) {
            if (mc == null) {
                mc = new MoveCommand(spl.getNodes(), MainApplication.getMap().mapView.getEastNorth(clickPos.x, clickPos.y), en);
                UndoRedoHandler.getInstance().add(mc);
                clickPos = null;
            } else
                mc.applyVectorTo(en);
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        clickPos = null;
        if (ph == null) return;
        if (ph.point == SplinePoint.ENDPOINT) {
            if (mc == null) {
                mc = new MoveCommand(ph.sn.node, ph.sn.node.getEastNorth(), en);
                UndoRedoHandler.getInstance().add(mc);
            } else
                mc.applyVectorTo(en);
        } else {
            if (dragControl) {
                UndoRedoHandler.getInstance().add(new Spline.EditSplineCommand(ph.sn));
                dragControl = false;
            }
            ph.movePoint(en);
            if (lockCounterpart) {
                ph.moveCounterpart(lockCounterpartLength);
            }
        }
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    Node nodeHighlight;
    short direction;

    @Override
    public void mouseMoved(MouseEvent e) {
        updateKeyModifiers(e);
        if (!MainApplication.getMap().mapView.isActiveLayerDrawable()) return;
        Spline spl = getSpline();
        if (spl == null) return;
        Point oldHelperEndpoint = helperEndpoint;
        PointHandle oldph = ph;
        boolean redraw = false;
        ph = spl.getNearestPoint(MainApplication.getMap().mapView, e.getPoint());
        if (ph == null)
            if (!ctrl && spl.doesHit(e.getX(), e.getY(), MainApplication.getMap().mapView)) {
                helperEndpoint = null;
                MainApplication.getMap().mapView.setNewCursor(Cursor.MOVE_CURSOR, this);
            } else {
                Node n = null;
                if (!ctrl)
                    n = MainApplication.getMap().mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
                if (n == null) {
                    redraw = removeHighlighting();
                    helperEndpoint = e.getPoint();
                    MainApplication.getMap().mapView.setNewCursor(cursor, this);
                } else {
                    redraw = setHighlight(n);
                    MainApplication.getMap().mapView.setNewCursor(cursorJoinNode, this);
                    helperEndpoint = MainApplication.getMap().mapView.getPoint(n);
                }
            }
        else {
            helperEndpoint = null;
            MainApplication.getMap().mapView.setNewCursor(cursorJoinWay, this);
            if (ph.point == SplinePoint.ENDPOINT)
                redraw = setHighlight(ph.sn.node);
            else
                redraw = removeHighlighting();
        }
        if (!drawHelperLine || spl.isClosed() || direction == 0)
            helperEndpoint = null;

        if (redraw || oldHelperEndpoint != helperEndpoint || (oldph == null && ph != null)
                || (oldph != null && !oldph.equals(ph)))
            MainApplication.getLayerManager().invalidateEditLayer();
    }

    /**
     * Repaint on mouse exit so that the helper line goes away.
     */
    @Override
    public void mouseExited(MouseEvent e) {
        if (!MainApplication.getMap().mapView.isActiveLayerDrawable())
            return;
        removeHighlighting();
        helperEndpoint = null;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    private boolean setHighlight(Node n) {
        if (nodeHighlight == n)
            return false;
        removeHighlighting();
        nodeHighlight = n;
        n.setHighlighted(true);
        return true;
    }

    /**
     * Removes target highlighting from primitives. Issues repaint if required.
     * Returns true if a repaint has been issued.
     */
    private boolean removeHighlighting() {
        if (nodeHighlight != null) {
            nodeHighlight.setHighlighted(false);
            nodeHighlight = null;
            return true;
        }
        return false;
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds box) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Spline spl = getSpline();
        if (spl == null)
            return;
        spl.paint(g, mv, rubberLineColor, Color.green, helperEndpoint, direction);
        spl.paintProposedNodes(g, mv);
        if (ph != null && (ph.point != SplinePoint.ENDPOINT || (nodeHighlight != null && nodeHighlight.isDeleted()))) {
            g.setColor(MapPaintSettings.INSTANCE.getSelectedColor());
            Point p = mv.getPoint(ph.getPoint());
            g.fillRect(p.x - 1, p.y - 1, 3, 3);
        }
    }

    @Override
    public boolean layerIsSupported(Layer l) {
        return isEditableDataLayer(l);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditLayer() != null);
    }

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        Spline spl = getSpline();
        if (spl == null) return;
        for (OsmPrimitive primitive : event.getPrimitives()) {
            if (primitive instanceof Node) {
                spl.removeNode((Node) primitive);
            }
        }
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {

    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {

    }

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {

    }

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {

    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {

    }

    @Override
    public void dataChanged(DataChangedEvent event) {

    }

    public static class BackSpaceAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            UndoRedoHandler.getInstance().undo();
        }
    }

    private Spline splCached;

    Spline getSpline() {
        if (splCached != null)
            return splCached;
        Layer l = getLayerManager().getEditLayer();
        if (!(l instanceof OsmDataLayer))
            return null;
        splCached = layerSplines.get(l);
        if (splCached == null)
            splCached = new Spline();
        layerSplines.put(l, splCached);
        return splCached;
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        splCached = layerSplines.get(MainApplication.getLayerManager().getActiveLayer());
    }

    Map<Layer, Spline> layerSplines = new HashMap<>();

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        // Do nothing
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        // Do nothing
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        layerSplines.remove(e.getRemovedLayer());
        splCached = null;
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE && ph != null) {
            Spline spl = ph.getSpline();
            if (spl.nodeCount() == 3 && spl.isClosed() && ph.idx == 1)
                return; // Don't allow to delete node when it results with two-node closed spline
            UndoRedoHandler.getInstance().add(spl.new DeleteSplineNodeCommand(ph.idx));
            MainApplication.getLayerManager().invalidateEditLayer();
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && direction != 0) {
            direction = 0;
            MainApplication.getLayerManager().invalidateEditLayer();
            e.consume();
        }
    }

    @Override
    public void doKeyReleased(KeyEvent e) {
        // Do nothing
    }

    protected void createSplineFromSelection() {
        DataSet ds = getLayerManager().getEditDataSet();
        if (ds == null) return;
        Way way = ds.getLastSelectedWay();
        if (way == null) return;
        if (way.getNodesCount() < 3) return;
        splCached = Spline.fromNodes(way.getNodes(), 0.5, way.isClosed());
    }

}
