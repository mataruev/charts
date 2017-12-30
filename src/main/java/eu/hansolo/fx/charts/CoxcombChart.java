/*
 * Copyright (c) 2017 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.fx.charts;

import eu.hansolo.fx.charts.data.ChartData;
import eu.hansolo.fx.charts.event.ChartDataEventListener;
import eu.hansolo.fx.charts.event.SelectionEvent;
import eu.hansolo.fx.charts.event.SelectionEventListener;
import eu.hansolo.fx.charts.series.Series;
import eu.hansolo.fx.charts.tools.Helper;
import eu.hansolo.fx.charts.tools.InfoPopup;
import eu.hansolo.fx.charts.tools.Order;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * User: hansolo
 * Date: 26.12.17
 * Time: 12:11
 */
@DefaultProperty("children")
public class CoxcombChart extends Region {
    private static final double                                       PREFERRED_WIDTH  = 250;
    private static final double                                       PREFERRED_HEIGHT = 250;
    private static final double                                       MINIMUM_WIDTH    = 50;
    private static final double                                       MINIMUM_HEIGHT   = 50;
    private static final double                                       MAXIMUM_WIDTH    = 1024;
    private static final double                                       MAXIMUM_HEIGHT   = 1024;
    private              double                                       size;
    private              double                                       width;
    private              double                                       height;
    private              Canvas                                       canvas;
    private              GraphicsContext                              ctx;
    private              Pane                                         pane;
    private              ObservableList<ChartData>                    items;
    private              Color                                        _textColor;
    private              ObjectProperty<Color>                        textColor;
    private              boolean                                      _autoTextColor;
    private              BooleanProperty                              autoTextColor;
    private              Order                                        _order;
    private              ObjectProperty<Order>                        order;
    private              ChartDataEventListener                       itemListener;
    private              ListChangeListener<ChartData>                itemListListener;
    private              EventHandler<MouseEvent>                     clickHandler;
    private              CopyOnWriteArrayList<SelectionEventListener> listeners;
    private              InfoPopup                                    popup;


    // ******************** Constructors **************************************
    public CoxcombChart() {
        this(new ArrayList<>());
    }
    public CoxcombChart(final ChartData... ITEMS) {
        this(Arrays.asList(ITEMS));
    }
    public CoxcombChart(final List<ChartData> ITEMS) {
        width            = PREFERRED_WIDTH;
        height           = PREFERRED_HEIGHT;
        size             = PREFERRED_WIDTH;
        items            = FXCollections.observableArrayList(ITEMS);
        _textColor       = Color.WHITE;
        _autoTextColor   = true;
        _order           = Order.DESCENDING;
        itemListener     = e -> reorder(getOrder());
        itemListListener = c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    c.getAddedSubList().forEach(addedItem -> addedItem.setOnChartDataEvent(itemListener));
                    reorder(getOrder());
                } else if (c.wasRemoved()) {
                    c.getRemoved().forEach(removedItem -> removedItem.removeChartDataEventListener(itemListener));
                    reorder(getOrder());
                }
            }
            redraw();
        };
        clickHandler     = e -> checkForClick(e);
        listeners        = new CopyOnWriteArrayList<>();
        initGraphics();
        registerListeners();
    }


    // ******************** Initialization ************************************
    private void initGraphics() {
        if (Double.compare(getPrefWidth(), 0.0) <= 0 || Double.compare(getPrefHeight(), 0.0) <= 0 || Double.compare(getWidth(), 0.0) <= 0 ||
            Double.compare(getHeight(), 0.0) <= 0) {
            if (getPrefWidth() > 0 && getPrefHeight() > 0) {
                setPrefSize(getPrefWidth(), getPrefHeight());
            } else {
                setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
            }
        }

        getStyleClass().add("coxcomb-chart");

        popup = new InfoPopup();

        canvas = new Canvas(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        ctx    = canvas.getGraphicsContext2D();

        ctx.setLineCap(StrokeLineCap.BUTT);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.setTextAlign(TextAlignment.CENTER);

        pane = new Pane(canvas);

        getChildren().setAll(pane);
    }

    private void registerListeners() {
        widthProperty().addListener(o -> resize());
        heightProperty().addListener(o -> resize());
        items.forEach(item -> item.setOnChartDataEvent(itemListener));
        items.addListener(itemListListener);
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, clickHandler);
        setOnSelectionEvent(e -> {
            popup.update(e);
            popup.animatedShow(getScene().getWindow());
        });
    }


    // ******************** Methods *******************************************
    @Override public void layoutChildren() {
        super.layoutChildren();
    }

    @Override protected double computeMinWidth(final double HEIGHT) { return MINIMUM_WIDTH; }
    @Override protected double computeMinHeight(final double WIDTH) { return MINIMUM_HEIGHT; }
    @Override protected double computePrefWidth(final double HEIGHT) { return super.computePrefWidth(HEIGHT); }
    @Override protected double computePrefHeight(final double WIDTH) { return super.computePrefHeight(WIDTH); }
    @Override protected double computeMaxWidth(final double HEIGHT) { return MAXIMUM_WIDTH; }
    @Override protected double computeMaxHeight(final double WIDTH) { return MAXIMUM_HEIGHT; }

    @Override public ObservableList<Node> getChildren() { return super.getChildren(); }

    public void dispose() {
        items.forEach(item -> item.removeChartDataEventListener(itemListener));
        items.removeListener(itemListListener);
    }

    public List<ChartData> getItems() { return items; }
    public void setItems(final ChartData... ITEMS) {
        setItems(Arrays.asList(ITEMS));
    }
    public void setItems(final List<ChartData> ITEMS) { items.setAll(ITEMS); }
    public void addItem(final ChartData ITEM) {
        if (!items.contains(ITEM)) {
            items.add(ITEM);
        }
    }
    public void addItems(final ChartData... ITEMS) {
        addItems(Arrays.asList(ITEMS));
    }
    public void addItems(final List<ChartData> ITEMS) {
        ITEMS.forEach(item -> addItem(item));
    }
    public void removeItem(final ChartData ITEM) {
        if (items.contains(ITEM)) {
            items.remove(ITEM);
        }
    }
    public void removeItems(final ChartData... ITEMS) {
        removeItems(Arrays.asList(ITEMS));
    }
    public void removeItems(final List<ChartData> ITEMS) {
        ITEMS.forEach(item -> removeItem(item));
    }

    public void sortItemsAscending() {
        Collections.sort(items, Comparator.comparingDouble(ChartData::getValue));
    }
    public void sortItemsDescending() {
        Collections.sort(items, Comparator.comparingDouble(ChartData::getValue).reversed());
    }

    public double sumOfAllItems() { return items.stream().mapToDouble(ChartData::getValue).sum(); }

    public Color getTextColor() { return null == textColor ? _textColor : textColor.get(); }
    public void setTextColor(final Color COLOR) {
        if (null == textColor) {
            _textColor = COLOR;
            redraw();
        } else {
            textColor.set(COLOR);
        }
    }
    public ObjectProperty<Color> textColorProperty() {
        if (null == textColor) {
            textColor = new ObjectPropertyBase<Color>(_textColor) {
                @Override protected void invalidated() { redraw(); }
                @Override public Object getBean() { return CoxcombChart.this; }
                @Override public String getName() { return "textColor"; }
            };
            _textColor = null;
        }
        return textColor;
    }

    public Order getOrder() { return null == order ? _order : order.get(); }
    public void setOrder(final Order ORDER) {
        if (null == order) {
            _order = ORDER;
            reorder(_order);
        } else {
            order.set(ORDER);
        }
    }
    public ObjectProperty<Order> orderProperty() {
        if (null == order) {
            order = new ObjectPropertyBase<Order>(_order) {
                @Override protected void invalidated() { reorder(get()); }
                @Override public Object getBean() { return CoxcombChart.this; }
                @Override public String getName() { return "order"; }
            };
            _order = null;
        }
        return order;
    }

    public boolean isAutoTextColor() { return null == autoTextColor ? _autoTextColor : autoTextColor.get(); }
    public void setAutoTextColor(final boolean AUTO) {
        if (null == autoTextColor) {
            _autoTextColor = AUTO;
            redraw();
        } else {
            autoTextColor.set(AUTO);
        }
    }
    public BooleanProperty autoTextColorProperty() {
        if (null == autoTextColor) {
            autoTextColor = new BooleanPropertyBase(_autoTextColor) {
                @Override protected void invalidated() { redraw(); }
                @Override public Object getBean() { return CoxcombChart.this; }
                @Override public String getName() { return "autoTextColor"; }
            };
        }
        return autoTextColor;
    }

    public void checkForClick(final MouseEvent EVT) {
        final double X = EVT.getX();
        final double Y = EVT.getY();

        popup.setX(EVT.getScreenX());
        popup.setY(EVT.getScreenY() - popup.getHeight());

        int    noOfChartData = items.size();
        double barWidth      = size * 0.04;
        double sum           = sumOfAllItems();
        double stepSize      = 360.0 / sum;
        double angle         = 0;
        double startAngle    = 0;
        double xy            = size * 0.32;
        double minWH         = size * 0.36;
        double maxWH         = size * 0.64;
        double wh            = minWH;
        double whStep        = (maxWH - minWH) / noOfChartData;

        for (int i = 0 ; i < noOfChartData ; i++) {
            ChartData item  = items.get(i);

            angle      = item.getValue() * stepSize;
            startAngle += angle;
            xy         -= (whStep / 2.0);
            wh         += whStep;
            barWidth   += whStep;

            // Check if x,y are in segment
            if (Helper.isInRingSegment(X, Y, xy, xy, wh, wh, Math.abs(360 - startAngle), angle, barWidth)) {
                fireSelectionEvent(new SelectionEvent(item));
                break;
            };
        }
    }

    private void reorder(final Order ORDER) {
        if (ORDER == Order.ASCENDING) {
            sortItemsAscending();
        } else {
            sortItemsDescending();
        }
    }


    // ******************** Event Handling ************************************
    public void setOnSelectionEvent(final SelectionEventListener LISTENER) { addSelectionEventListener(LISTENER); }
    public void addSelectionEventListener(final SelectionEventListener LISTENER) { if (!listeners.contains(LISTENER)) listeners.add(LISTENER); }
    public void removeSelectionEventListener(final SelectionEventListener LISTENER) { if (listeners.contains(LISTENER)) listeners.remove(LISTENER); }
    public void removeAllSelectionEventListeners() { listeners.clear(); }

    public void fireSelectionEvent(final SelectionEvent EVENT) {
        for (SelectionEventListener listener : listeners) { listener.onSelectionEvent(EVENT); }
    }


    // ******************** Drawing *******************************************
    private void drawChart() {
        int          noOfChartDatas   = items.size();
        double       center      = size * 0.5;
        double       barWidth    = size * 0.04;
        double       sum         = sumOfAllItems();
        double       stepSize    = 360.0 / sum;
        double       angle       = 0;
        double       startAngle  = 90;
        double       xy          = size * 0.32;
        double       minWH       = size * 0.36;
        double       maxWH       = size * 0.64;
        double       wh          = minWH;
        double       whStep      = (maxWH - minWH) / noOfChartDatas;
        Color        textColor   = getTextColor();
        boolean      isAutoColor = isAutoTextColor();
        DropShadow   shadow      = new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.75), size * 0.02, 0, 0, 0);
        double       spread      = size * 0.005;
        double       x, y;
        double       tx, ty;
        double       endAngle;
        double       radius;
        double       clippingRadius;

        ctx.clearRect(0, 0, size, size);
        ctx.setFont(Font.font(size * 0.03));
        for (int i = 0 ; i < noOfChartDatas ; i++) {
            ChartData   item  = items.get(i);
            double value = item.getValue();

            startAngle += angle;
            xy         -= (whStep / 2.0);
            wh         += whStep;
            barWidth   += whStep;

            angle          = value * stepSize;
            endAngle       = startAngle + angle;
            radius         = wh * 0.5;
            clippingRadius = radius + barWidth * 0.5;

            // Set Segment Clipping
            ctx.save();
            ctx.beginPath();
            ctx.arc(center, center, clippingRadius, clippingRadius, 0, 360);
            ctx.clip();

            // Segment
            ctx.save();
            // Draw segment
            ctx.setLineWidth(barWidth);
            ctx.setStroke(item.getFillColor());
            ctx.strokeArc(xy, xy, wh, wh, startAngle, angle, ArcType.OPEN);
            // Add shadow effect to segment
            if (i != (noOfChartDatas-1) && angle > 2) {
                x = Math.cos(Math.toRadians(endAngle - 5));
                y = -Math.sin(Math.toRadians(endAngle - 5));
                shadow.setOffsetX(x * spread);
                shadow.setOffsetY(y * spread);
                ctx.save();
                ctx.setEffect(shadow);
                ctx.strokeArc(xy, xy, wh, wh, endAngle, 2, ArcType.OPEN);
                ctx.restore();
                if (i == 0) {
                    x = Math.cos(Math.toRadians(startAngle + 5));
                    y = -Math.sin(Math.toRadians(startAngle + 5));
                    shadow.setOffsetX(x * spread);
                    shadow.setOffsetY(y * spread);
                    ctx.setEffect(shadow);
                    ctx.strokeArc(xy, xy, wh, wh, startAngle, -2, ArcType.OPEN);
                }
            }
            ctx.restore();

            // Remove Segment Clipping
            ctx.restore();

            // Percentage
            if (angle > 8) {
                tx = center + radius * Math.cos(Math.toRadians(endAngle - angle * 0.5));
                ty = center - radius * Math.sin(Math.toRadians(endAngle - angle * 0.5));
                if (isAutoColor) {
                    ctx.setFill(Helper.isDark(item.getFillColor()) ? Color.WHITE : Color.BLACK);
                } else {
                    ctx.setFill(textColor);
                }
                ctx.fillText(String.format(Locale.US, "%.0f%%", (value / sum * 100.0)), tx, ty, barWidth);
            }
        }
    }


    // ******************** Resizing ******************************************
    private void resize() {
        width = getWidth() - getInsets().getLeft() - getInsets().getRight();
        height = getHeight() - getInsets().getTop() - getInsets().getBottom();
        size = width < height ? width : height;

        if (width > 0 && height > 0) {
            pane.setMaxSize(size, size);
            pane.setPrefSize(size, size);
            pane.relocate((getWidth() - size) * 0.5, (getHeight() - size) * 0.5);

            canvas.setWidth(size);
            canvas.setHeight(size);

            redraw();
        }
    }

    private void redraw() {
        drawChart();
    }
}